package com.br.minehub.main.service.pterodactyl.model;

public class PteroServer {

    private final String identifier;
    private final String name;

    public PteroServer(String identifier, String name) {
        this.identifier = identifier;
        this.name = name;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name + " (" + identifier + ")";
    }
}