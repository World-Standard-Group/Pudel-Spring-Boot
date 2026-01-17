/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 Napapon Kamanee
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package worldstandard.group.pudel.api;

/**
 * Contains metadata about a Pudel plugin.
 */
public class PluginInfo {
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String[] dependencies;

    public PluginInfo(String name, String version, String author, String description) {
        this(name, version, author, description, new String[0]);
    }

    public PluginInfo(String name, String version, String author, String description, String[] dependencies) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.dependencies = dependencies != null ? dependencies : new String[0];
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String[] getDependencies() {
        return dependencies;
    }
}

