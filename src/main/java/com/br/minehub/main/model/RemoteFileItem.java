package com.br.minehub.main.model;

public class RemoteFileItem {

    private final String name;
    private final String type;
    private final String size;
    private final boolean directory;

    public RemoteFileItem(String name, String type, String size, boolean directory) {
        this.name = name;
        this.type = type;
        this.size = size;
        this.directory = directory;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getSize() {
        return size;
    }

    public boolean isDirectory() {
        return directory;
    }
}