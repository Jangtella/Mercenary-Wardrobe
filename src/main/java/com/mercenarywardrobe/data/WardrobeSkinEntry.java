package com.mercenarywardrobe.data;

public record WardrobeSkinEntry(
        int contentId,
        String name,
        String author,
        boolean slim,
        long timestamp,
        String type
) {
    public WardrobeSkinEntry(int contentId, String name, String author, boolean slim, long timestamp) {
        this(contentId, name, author, slim, timestamp, "skin");
    }

    public WardrobeSkinEntry withType(String newType) {
        return new WardrobeSkinEntry(contentId, name, author, slim, timestamp, newType);
    }
}
