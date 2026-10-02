package group.worldstandard.pudel.core.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import group.worldstandard.pudel.api.database.Column;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.QueryBuilder;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class PluginRepositoryImpl<T> implements PluginRepository<T> {

    private static final Logger logger = LoggerFactory.getLogger(PluginRepositoryImpl.class);

    private final PluginDatabaseManagerImpl dbManager;
    private final String tableName;
    private final String fullTableName;
    private final Class<T> entityClass;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final BeanPropertyRowMapper<T> rowMapper;

    private final Map<String, String> fieldToColumn = new LinkedHashMap<>();
    private final Map<String, Field> columnToField = new LinkedHashMap<>();

    public PluginRepositoryImpl(PluginDatabaseManagerImpl dbManager, String tableName,
                                Class<T> entityClass, JdbcTemplate jdbcTemplate) {
        this.dbManager = dbManager;
        this.tableName = tableName;
        this.fullTableName = dbManager.getFullTableName(tableName);
        this.entityClass = entityClass;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

        BeanPropertyRowMapper<T> mapper = new BeanPropertyRowMapper<>(entityClass);
        mapper.setConversionService(createConversionService());
        this.rowMapper = mapper;

        buildFieldMappings();
    }

    private ConversionService createConversionService() {
        DefaultConversionService conversionService =
            new DefaultConversionService();

        conversionService.addConverter(Timestamp.class, Instant.class, Timestamp::toInstant);

        conversionService.addConverter(Timestamp.class, LocalDateTime.class, Timestamp::toLocalDateTime);

        conversionService.addConverter(Timestamp.class, OffsetDateTime.class,
            ts -> ts.toInstant().atOffset(java.time.ZoneOffset.UTC));

        return conversionService;
    }

    private void buildFieldMappings() {
        for (Field field : entityClass.getDeclaredFields()) {
            field.setAccessible(true);

            Column columnAnnotation = field.getAnnotation(Column.class);
            if (columnAnnotation != null && columnAnnotation.ignore()) {
                continue;
            }

            String columnName;
            if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                columnName = columnAnnotation.name();
            } else {
                columnName = toSnakeCase(field.getName());
            }

            // Identifiers cannot be bound as PreparedStatement parameters, so every
            // column name is validated here — at the single point where the mapping is
            // built — before it can reach any interpolated SQL in insert()/update().
            validateColumn(columnName);

            fieldToColumn.put(field.getName(), columnName);
            columnToField.put(columnName, field);
        }
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    @Override
    public T save(T entity) {
        try {
            Long id = getEntityId(entity);
            if (id == null || id == 0) {
                return insert(entity);
            } else {
                return update(entity);
            }
        } catch (Exception e) {
            logger.error("Error saving entity: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save entity", e);
        }
    }

    private T insert(T entity) throws Exception {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<String> defaultedColumns = new ArrayList<>();

        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();

            if (columnName.equals("id") || columnName.equals("created_at") || columnName.equals("updated_at")) {
                continue; // Skip auto-generated columns
            }

            Field field = entityClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);

            // If the field is null and the column has a SQL DEFAULT defined,
            // skip it from the INSERT so the DB applies the default instead
            if (value == null) {
                Column colAnnot = field.getAnnotation(Column.class);
                if (colAnnot != null && !colAnnot.defaultValue().isEmpty()) {
                    defaultedColumns.add(columnName);
                    continue;
                }
            }

            columns.add(columnName);
            values.add(convertToJdbcValue(value));
        }

        // Build RETURNING clause: always id + timestamps + any defaulted columns
        List<String> returning = new ArrayList<>();
        returning.add("id");
        returning.add("created_at");
        returning.add("updated_at");
        returning.addAll(defaultedColumns);

        // Re-validate immediately before interpolation. Column names originate from
        // @Column(name = ...) annotations rather than user input, but an annotation is
        // still third-party code: a plugin author (or a compromised build) could name a
        // column "id); DROP TABLE x --". Validating at the point of use keeps the
        // guarantee local to the query instead of relying on a distant call site.
        columns.forEach(this::validateColumn);
        returning.forEach(this::validateColumn);
        validateQualifiedTableName(fullTableName);

        String sql = "INSERT INTO " + fullTableName + " (" + String.join(", ", columns) + ") " +
                "VALUES (" + columns.stream().map(c -> "?").collect(Collectors.joining(", ")) + ") " +
                "RETURNING " + String.join(", ", returning);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, returning.toArray(new String[0]));
            for (int i = 0; i < values.size(); i++) {
                setParameterValue(ps, i + 1, values.get(i));
            }
            return ps;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            Number generatedId = (Number) keys.get("id");
            if (generatedId != null) {
                setEntityId(entity, generatedId.longValue());
            }
            readGeneratedTimestamp(entity, keys, "created_at");
            readGeneratedTimestamp(entity, keys, "updated_at");
            for (String colName : defaultedColumns) {
                readGeneratedColumn(entity, keys, colName);
            }
        }

        return entity;
    }

    private T update(T entity) throws Exception {
        Long id = getEntityId(entity);
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();

            if (columnName.equals("id") || columnName.equals("created_at")) {
                continue; // Skip id and created_at
            }

            if (columnName.equals("updated_at")) {
                setClauses.add(columnName + " = CURRENT_TIMESTAMP");
                continue;
            }

            Field field = entityClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);

            setClauses.add(columnName + " = ?");
            values.add(convertToJdbcValue(value));
        }

        values.add(id); // For WHERE clause

        validateQualifiedTableName(fullTableName);

        String sql = "UPDATE " + fullTableName + " SET " + String.join(", ", setClauses) + " WHERE id = ?";
        jdbcTemplate.update(sql, values.toArray());

        return entity;
    }

    private Long getEntityId(T entity) throws Exception {
        Field idField = columnToField.get("id");
        if (idField == null) {
            throw new IllegalStateException("Entity must have an 'id' field");
        }
        idField.setAccessible(true);
        Object value = idField.get(entity);
        return switch (value) {
            case null -> null;
            case Long l -> l;
            case Number number -> number.longValue();
            default -> null;
        };
    }

    private void setEntityId(T entity, long id) throws Exception {
        Field idField = columnToField.get("id");
        if (idField != null) {
            idField.setAccessible(true);
            idField.set(entity, id);
        }
    }

    private void readGeneratedTimestamp(T entity, Map<String, Object> keys, String colName) throws Exception {
        Object value = keys.get(colName);
        if (value == null) return;
        Field field = columnToField.get(colName);
        if (field == null) return;
        field.setAccessible(true);
        if (value instanceof Timestamp ts) {
            field.set(entity, ts.toInstant());
        } else if (value instanceof Instant instant) {
            field.set(entity, instant);
        } else {
            field.set(entity, value);
        }
    }

    private void readGeneratedColumn(T entity, Map<String, Object> keys, String colName) throws Exception {
        Object value = keys.get(colName);
        if (value == null) return;
        Field field = columnToField.get(colName);
        if (field == null) return;
        field.setAccessible(true);
        if (field.getType().isInstance(value)) {
            field.set(entity, value);
        } else {
            // Attempt type coercion for numeric widening/narrowing
            if (value instanceof Number num) {
                Class<?> ft = field.getType();
                if (ft == Long.class || ft == long.class) {
                    field.set(entity, num.longValue());
                } else if (ft == Integer.class || ft == int.class) {
                    field.set(entity, num.intValue());
                } else if (ft == Short.class || ft == short.class) {
                    field.set(entity, num.shortValue());
                } else if (ft == Double.class || ft == double.class) {
                    field.set(entity, num.doubleValue());
                } else if (ft == Float.class || ft == float.class) {
                    field.set(entity, num.floatValue());
                } else {
                    field.set(entity, value);
                }
            } else {
                field.set(entity, value);
            }
        }
    }

    private Object convertToJdbcValue(Object value) {
        return switch (value) {
            case null -> null;

            case Instant instant -> Timestamp.from(instant);

            case LocalDateTime ldt -> Timestamp.valueOf(ldt);

            case OffsetDateTime odt -> Timestamp.from(odt.toInstant());
            default -> value;
        };
    }

    private void setParameterValue(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NULL);
        } else {
            ps.setObject(index, value);
        }
    }

    @Override
    public List<T> saveAll(Iterable<T> entities) {
        List<T> result = new ArrayList<>();
        for (T entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public Optional<T> findById(long id) {
        String sql = "SELECT * FROM " + fullTableName + " WHERE id = ?";
        List<T> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<T> findAll() {
        String sql = "SELECT * FROM " + fullTableName + " ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<T> findAll(int limit, int offset) {
        String sql = "SELECT * FROM " + fullTableName + " ORDER BY id LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, limit, offset);
    }

    @Override
    public List<T> findBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT * FROM " + fullTableName + " WHERE " + column + " = ? ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper, value);
    }

    @Override
    public List<T> findBy(String column, Object value, int limit, int offset) {
        validateColumn(column);
        String sql = "SELECT * FROM " + fullTableName + " WHERE " + column + " = ? ORDER BY id LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, value, limit, offset);
    }

    @Override
    public List<T> findByAll(Map<String, Object> conditions) {
        if (conditions.isEmpty()) {
            return findAll();
        }
        conditions.keySet().forEach(this::validateColumn);

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(fullTableName).append(" WHERE ");
        List<String> clauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            clauses.add(entry.getKey() + " = ?");
            values.add(entry.getValue());
        }

        sql.append(String.join(" AND ", clauses)).append(" ORDER BY id");
        return jdbcTemplate.query(sql.toString(), rowMapper, values.toArray());
    }

    @Override
    public Optional<T> findOneBy(String column, Object value) {
        List<T> results = findBy(column, value);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<T> findOneByAll(Map<String, Object> conditions) {
        List<T> results = findByAll(conditions);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public boolean existsById(long id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM " + fullTableName + " WHERE id = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    @Override
    public boolean existsBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT EXISTS(SELECT 1 FROM " + fullTableName + " WHERE " + column + " = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, value));
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + fullTableName;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT COUNT(*) FROM " + fullTableName + " WHERE " + column + " = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, value);
        return count != null ? count : 0;
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM " + fullTableName + " WHERE id = ?";
        int rows = jdbcTemplate.update(sql, id);
        return rows > 0;
    }

    @Override
    public boolean delete(T entity) {
        try {
            Long id = getEntityId(entity);
            if (id == null) return false;
            return deleteById(id);
        } catch (Exception e) {
            logger.error("Error deleting entity: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int deleteAll(Iterable<T> entities) {
        int count = 0;
        for (T entity : entities) {
            if (delete(entity)) count++;
        }
        return count;
    }

    @Override
    public int deleteBy(String column, Object value) {
        validateColumn(column);
        String sql = "DELETE FROM " + fullTableName + " WHERE " + column + " = ?";
        return jdbcTemplate.update(sql, value);
    }

    @Override
    public int deleteAll() {
        String sql = "DELETE FROM " + fullTableName;
        return jdbcTemplate.update(sql);
    }

    @Override
    public QueryBuilder<T> query() {
        return new QueryBuilderImpl<>(this, fullTableName, entityClass, jdbcTemplate, rowMapper);
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    private void validateColumn(String column) {
        if (column == null || !column.matches("^[a-z][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid column name: " + column);
        }
    }

    /**
     * Validates a schema-qualified table name before it is interpolated into SQL.
     * <p>
     * Accepts the {@code schema.table} form produced by
     * {@link PluginDatabaseManagerImpl#getFullTableName(String)}, optionally quoted.
     * Identifiers cannot be parameterised, so they are checked against a strict
     * allowlist instead of being escaped.
     *
     * @param qualifiedName the schema-qualified table name to validate
     * @throws IllegalArgumentException if the name is null or contains anything
     *                                  other than the allowed identifier characters
     */
    private void validateQualifiedTableName(String qualifiedName) {
        if (qualifiedName == null
                || !qualifiedName.matches("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid table name: " + qualifiedName);
        }
    }
}