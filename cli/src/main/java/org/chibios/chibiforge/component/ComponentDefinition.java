/*
    ChibiOS - Copyright (C) 2025-2026 Giovanni Di Sirio.

    This file is part of ChibiOS.

    ChibiOS is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation version 3 of the License.

    ChibiOS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chibios.chibiforge.component;

import java.util.List;

/**
 * A parsed component definition from schema.xml.
 * Contains all metadata and the configuration schema (sections, properties).
 */
public class ComponentDefinition {
    private final String id;
    private final String name;
    private final String version;
    private final boolean hidden;
    private final boolean isPlatform;
    private final String description;
    private final List<ResourceDef> resources;
    private final List<String> categories;
    private final List<FeatureDef> requires;
    private final List<FeatureDef> provides;
    private final List<SectionDef> sections;
    private final List<ImageDef> images; // component-level images

    public ComponentDefinition(String id, String name, String version, boolean hidden, boolean isPlatform,
                               String description, List<ResourceDef> resources, List<String> categories,
                               List<FeatureDef> requires, List<FeatureDef> provides,
                               List<SectionDef> sections, List<ImageDef> images) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.hidden = hidden;
        this.isPlatform = isPlatform;
        this.description = description;
        this.resources = resources != null ? List.copyOf(resources) : List.of();
        this.categories = categories != null ? List.copyOf(categories) : List.of();
        this.requires = requires != null ? List.copyOf(requires) : List.of();
        this.provides = provides != null ? List.copyOf(provides) : List.of();
        this.sections = sections != null ? List.copyOf(sections) : List.of();
        this.images = images != null ? List.copyOf(images) : List.of();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public boolean isHidden() { return hidden; }
    public boolean isPlatform() { return isPlatform; }
    public String getDescription() { return description; }
    public List<ResourceDef> getResources() { return resources; }
    public List<String> getCategories() { return categories; }
    public List<FeatureDef> getRequires() { return requires; }
    public List<FeatureDef> getProvides() { return provides; }
    public List<SectionDef> getSections() { return sections; }
    public List<ImageDef> getImages() { return images; }

    @Override
    public String toString() {
        return "ComponentDefinition{id='" + id + "', name='" + name + "', version='" + version + "'}";
    }
}
