package io.quarkus.annotation.processor.documentation.config.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ConfigItemCollection {

    List<AbstractConfigItem> getItems();

    @JsonIgnore
    default List<ConfigProperty> getProperties() {
        return getItems().stream()
                .filter(i -> i instanceof ConfigProperty)
                .map(ConfigProperty.class::cast)
                .toList();
    }

    @JsonIgnore
    default List<ConfigSection> getSections() {
        return getItems().stream()
                .filter(i -> i instanceof ConfigSection)
                .map(ConfigSection.class::cast)
                .toList();
    }

    @JsonIgnore
    default List<AbstractConfigItem> getNonDeprecatedItems() {
        return getItems().stream()
                .filter(i -> (i instanceof ConfigSection)
                        ? !i.isDeprecated() && !((ConfigSection) i).getNonDeprecatedItems().isEmpty()
                        : !i.isDeprecated())
                .toList();
    }

    @JsonIgnore
    default List<ConfigProperty> getNonDeprecatedProperties() {
        return getItems().stream()
                .filter(i -> i instanceof ConfigProperty && !i.isDeprecated())
                .map(ConfigProperty.class::cast)
                .toList();
    }

    @JsonIgnore
    default List<ConfigSection> getNonDeprecatedSections() {
        return getItems().stream()
                .filter(i -> i instanceof ConfigSection && !i.isDeprecated())
                .map(ConfigSection.class::cast)
                .toList();
    }

    void addItem(AbstractConfigItem item);

    boolean hasDurationType();

    boolean hasMemorySizeType();

    /**
     * Whether a table that lists all of this collection's items, deprecated ones included, shows a {@code Duration}
     * property. Nested sections count only their non-deprecated items, as the section tag renders them.
     */
    default boolean hasDurationTypeIncludingDeprecated() {
        return getItems().stream().anyMatch(AbstractConfigItem::hasDurationType);
    }

    /**
     * Whether a table that lists all of this collection's items, deprecated ones included, shows a {@code MemorySize}
     * property. Nested sections count only their non-deprecated items, as the section tag renders them.
     */
    default boolean hasMemorySizeTypeIncludingDeprecated() {
        return getItems().stream().anyMatch(AbstractConfigItem::hasMemorySizeType);
    }
}
