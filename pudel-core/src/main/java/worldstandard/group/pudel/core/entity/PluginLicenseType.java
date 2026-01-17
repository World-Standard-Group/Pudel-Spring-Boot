/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package worldstandard.group.pudel.core.entity;

/**
 * License types for plugins in the marketplace.
 * <p>
 * The Pudel Plugin API (PDK) is MIT licensed, which allows developers to create
 * plugins under any license, including proprietary/commercial licenses.
 * </p>
 */
public enum PluginLicenseType {

    /**
     * MIT License - Most permissive, allows commercial use without source disclosure.
     * Recommended for open source plugins.
     */
    MIT("MIT License", true, false),

    /**
     * Apache 2.0 License - Permissive with patent clause.
     */
    APACHE_2("Apache 2.0", true, false),

    /**
     * GPL v3 License - Copyleft, requires derivative works to be GPL.
     */
    GPL_3("GPL v3", true, true),

    /**
     * AGPL v3 License - Network copyleft, requires source for network use.
     */
    AGPL_3("AGPL v3", true, true),

    /**
     * Proprietary/Commercial - Closed source, may require payment.
     * The PDK's MIT license allows this for plugins.
     */
    PROPRIETARY("Proprietary", false, false),

    /**
     * Custom License - Plugin author defines their own terms.
     * Users should review the source URL for license details.
     */
    CUSTOM("Custom", false, false),

    /**
     * Exclusive License - For private plugins sold to specific clients.
     * Source is not publicly available.
     */
    EXCLUSIVE("Exclusive/Private", false, false);

    private final String displayName;
    private final boolean openSource;
    private final boolean copyleft;

    PluginLicenseType(String displayName, boolean openSource, boolean copyleft) {
        this.displayName = displayName;
        this.openSource = openSource;
        this.copyleft = copyleft;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return true if this license requires source code to be available
     */
    public boolean isOpenSource() {
        return openSource;
    }

    /**
     * @return true if this is a copyleft license (GPL, AGPL)
     */
    public boolean isCopyleft() {
        return copyleft;
    }

    /**
     * @return true if this license allows commercial closed-source plugins
     */
    public boolean allowsCommercialClosedSource() {
        return !copyleft && !openSource || this == MIT || this == APACHE_2;
    }
}

