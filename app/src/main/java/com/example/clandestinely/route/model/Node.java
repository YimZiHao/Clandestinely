package com.example.clandestinely.route.model;

/** One point on the campus map: a named place (FSKTM, LIB, ...) or a path junction (J001, ...). */
public final class Node {

    public static final String GROUP_JUNCTION = "junction";

    private final String id;
    private final String name;
    private final String group;
    private final double lat;
    private final double lon;
    private final boolean verified;

    public Node(String id, String name, String group, double lat, double lon, boolean verified) {
        this.id = id;
        this.name = name;
        this.group = group;
        this.lat = lat;
        this.lon = lon;
        this.verified = verified;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getGroup() { return group; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }

    /** True once someone has checked the pin is in the right place. */
    public boolean isVerified() { return verified; }

    /** Junctions are only used for routing; they are not shown as markers or in the pickers. */
    public boolean isJunction() { return GROUP_JUNCTION.equals(group); }
}
