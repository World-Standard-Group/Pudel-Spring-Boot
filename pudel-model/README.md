# Pudel Model Module

This module provides the AI/ML brain functionality for Pudel, including:

- **Ollama Client**: HTTP client for local LLM inference
- **Embedding Service**: ONNX-based sentence embeddings for semantic search

## Architecture

```
Discord Message → Mention Check → Context Builder → Embedding → Memory Search → LLM → Response
                       ↓
              (passive tracking for non-mentions)
```

## Prerequisites

### 1. Install Ollama

Ollama runs LLMs locally on your machine. Download from: https://ollama.ai

**Windows:**
```bash
# Download installer from https://ollama.ai/download/windows
```

**Linux:**
```bash
curl -fsSL https://ollama.ai/install.sh | sh
```

**macOS:**
```bash
brew install ollama
```

### 2. Start Ollama Server

```bash
ollama serve
```

The server runs on `http://localhost:11434` by default.

### 3. Pull a Model

Recommended lightweight models:

```bash
# Phi-3 Mini (3.8B params, ~2GB VRAM) - Recommended
ollama pull phi3:mini

# Gemma 2B (2B params, ~1.5GB VRAM)
ollama pull gemma2:2b

# Llama 3.2 1B (1B params, ~1GB VRAM) - Smallest
ollama pull llama3.2:1b

# Mistral 7B (7B params, ~4GB VRAM) - Better quality
ollama pull mistral:7b
```

### 4. (Optional) Local Embeddings

For local embeddings without Ollama, download the ONNX model:

1. Download `all-MiniLM-L6-v2.onnx` from HuggingFace:
   https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2

2. Place in `pudel-core/src/main/resources/embeddings/`:
   - `all-MiniLM-L6-v2.onnx`
   - `tokenizer.json`

## Configuration

Configure in `application.yml`:

```yaml
pudel:
  ollama:
    enabled: true
    base-url: http://localhost:11434
    model: phi3:mini
    temperature: 0.7
    max-tokens: 256
    timeout-seconds: 60
    # For thinking models (qwen3, deepseek), disable thinking mode
    disable-thinking: true
    
  embedding:
    enabled: false  # Enable if you have the ONNX model
    model-path: classpath:embeddings/all-MiniLM-L6-v2.onnx
    dimension: 384
```

### Thinking Models (qwen3, deepseek)

Some newer models like `qwen3` and `deepseek` use a "thinking" mode where they wrap their reasoning in `<think>...</think>` tags. This can consume many tokens before producing an actual response.

**Solutions:**

1. **Disable thinking mode** (recommended): Set `disable-thinking: true` in config. This adds `/no_think` to the system prompt.

2. **Increase max-tokens**: For thinking models, use 512-1024 tokens instead of 256 to give enough room for both thinking and response.

3. **Use a non-thinking model**: Models like `phi3:mini`, `gemma2:2b`, `llama3.2` don't have this behavior.

```yaml
pudel:
  ollama:
    model: qwen3:8b
    max-tokens: 512           # Increase for thinking models
    disable-thinking: true     # Disable thinking mode
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_ENABLED` | `true` | Enable/disable Ollama |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `phi3:mini` | Model to use |
| `EMBEDDING_ENABLED` | `false` | Enable local embeddings |

## How It Works

### Response Generation Flow

1. **User sends message** mentioning Pudel
2. **Context Builder** retrieves:
   - Recent conversation history (limited by subscription tier)
   - Relevant memories via semantic search
   - Guild/user personality settings
3. **Prompt Construction** builds messages for Ollama:
   - System prompt with personality traits
   - Memory context
   - Conversation history
   - Current user message
4. **Ollama LLM** generates response
5. **Response** sent to Discord

### Fallback Behavior

If Ollama is unavailable, Pudel falls back to:
- Template-based responses using pattern-based intent detection
- Basic sentiment analysis
- No external API calls required

### Memory Capacity

Memory is limited by subscription tier:
- **Free**: 1000 user rows, 5000 guild rows
- **Tier 1**: 1500 user rows, 7500 guild rows
- **Tier 2**: 2000 user rows, 10000 guild rows

Old memories are automatically pruned when capacity is reached.

## API

### PudelModelService

Main service for brain functionality:

```java
// Generate a response
GenerationRequest request = GenerationRequest.builder()
    .userMessage("Hello Pudel!")
    .personality(traits)
    .conversationHistory(history)
    .relevantMemories(memories)
    .build();
    
GenerationResponse response = modelService.generateResponse(request);

// Generate embeddings
Optional<float[]> embedding = modelService.generateEmbedding(text);

// Check health
ModelHealth health = modelService.getHealth();
```

### OllamaClient

Direct Ollama API access:

```java
// Chat completion
List<ChatMessage> messages = List.of(
    ChatMessage.system("You are helpful"),
    ChatMessage.user("Hello!")
);
Optional<String> response = ollamaClient.chat(messages);

// Simple generation
Optional<String> response = ollamaClient.generate(prompt, systemPrompt);

// Check availability
boolean available = ollamaClient.isAvailable();
```

## Troubleshooting

### Ollama not connecting

1. Ensure Ollama is running: `ollama serve`
2. Check URL in config matches Ollama's port
3. Check firewall allows localhost:11434

### Model not found

```bash
# Pull the model
ollama pull phi3:mini

# List available models
ollama list
```

### Out of memory

Try a smaller model:
```bash
ollama pull llama3.2:1b  # Only 1GB VRAM
```

Or adjust context window:
```yaml
pudel:
  ollama:
    context-window: 2048  # Reduce from 4096
```

### Slow responses

1. Use GPU if available (NVIDIA with CUDA)
2. Use smaller model (llama3.2:1b)
3. Reduce max-tokens
4. Keep model loaded: `keep-alive-duration: 30m`

## License

This module is part of Pudel and is licensed under AGPLv3.

