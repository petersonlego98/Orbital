package orbital.core;

import arc.util.*;

import java.util.*;

/** A display resolution that can be offered by the desktop graphics settings. */
public final class WindowResolution{
    public final int width;
    public final int height;
    /** Highest refresh rate found for this resolution; zero when it is not known. */
    public final int refreshRate;

    public WindowResolution(int width, int height, int refreshRate){
        if(width <= 0 || height <= 0) throw new IllegalArgumentException("Resolution dimensions must be positive");
        this.width = width;
        this.height = height;
        this.refreshRate = Math.max(0, refreshRate);
    }

    public WindowResolution(int width, int height){
        this(width, height, 0);
    }

    /** Stable value stored in the settings file. */
    public String id(){
        return width + "x" + height;
    }

    /** Parses a stored resolution value; invalid or automatic values return null. */
    public static @Nullable WindowResolution parse(@Nullable String value){
        if(value == null) return null;

        String normalized = value.trim().toLowerCase();
        int separator = normalized.indexOf('x');
        if(separator <= 0 || separator == normalized.length() - 1) return null;

        try{
            int width = Integer.parseInt(normalized.substring(0, separator));
            int height = Integer.parseInt(normalized.substring(separator + 1));
            if(width <= 0 || height <= 0) return null;
            return new WindowResolution(width, height);
        }catch(NumberFormatException ignored){
            return null;
        }
    }

    @Override
    public boolean equals(Object object){
        if(this == object) return true;
        if(!(object instanceof WindowResolution other)) return false;
        return width == other.width && height == other.height;
    }

    @Override
    public int hashCode(){
        return Objects.hash(width, height);
    }

    @Override
    public String toString(){
        return id();
    }
}
