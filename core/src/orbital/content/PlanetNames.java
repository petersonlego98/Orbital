package orbital.content;

import java.util.Locale;
import java.util.Random;

/**
 * Procedural planet names.
 *
 * Names are stable: everything is derived from a key, so the same key always returns the same
 * name. This is important because planet names are stored in saves and user settings.
 */
public final class PlanetNames{
    private static final String[] prefixes = {
    "Ae", "Al", "An", "Ar", "Be", "Ca", "Cy", "Da", "El", "En", "Fa",
    "Ga", "He", "Ia", "Il", "Ka", "Ke", "La", "Le", "Lu", "Ma", "Ne",
    "Ni", "No", "Pa", "Pe", "Qua", "Ra", "Re", "Sa", "Se", "Si", "Ta",
    "Te", "Va", "Ve", "Vi", "Ze", "Zo"
    };

    private static final String[] middles = {
    "ran", "len", "mir", "dor", "vas", "tia", "nor", "lis", "ron", "dara",
    "neth", "lune", "tera", "ven", "arion", "thel", "sira", "myr", "quon", "bora"
    };

    private static final String[] suffixes = {
    "a", "e", "i", "o", "us", "um", "ar", "on", "ia", "ea",
    "ara", "ora", "ine", "eth", "ath", "os", "yne", "ura"
    };

    private PlanetNames(){
    }

    /** Generates a stable name for the given key. */
    public static String generate(String key){
        return generate(key, 1);
    }

    /** Generates a stable name by joining `wordCount` syllables together. */
    public static String generate(String key, int wordCount){
        Random random = new Random(mix(key, wordCount));

        StringBuilder name = new StringBuilder(capitalize(pick(random, prefixes)));
        int joins = Math.max(0, wordCount - 1);

        for(int i = 0; i < joins; i++){
            if(i > 0 && random.nextInt(4) == 0){
                //sometimes drop the middle for shorter, cleaner names
                continue;
            }
            name.append(pick(random, middles));
            if(i == joins - 1){
                name.append(pick(random, suffixes));
            }
        }

        return name.toString();
    }

    private static String capitalize(String string){
        return string.substring(0, 1).toUpperCase(Locale.ROOT) + string.substring(1);
    }

    private static String pick(Random random, String[] values){
        return values[random.nextInt(values.length)];
    }

    private static long mix(String key, int wordCount){
        long hash = 1125899906842597L;
        for(int i = 0; i < key.length(); i++){
            hash = 31L * hash + key.charAt(i);
        }
        return hash + wordCount * 2654435761L;
    }
}
