package dev.poldi.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Kleine Reflection-Hilfe fuer Stellen, deren Namen sich zwischen Minecraft-Versionen oft aendern
 * (Kamera, Weltzeit, Spieloptionen, Sound-Listener). Probiert mehrere Namen der Reihe nach;
 * schlaegt alles fehl, gibt es null zurueck statt abzustuerzen.
 */
public final class PoldiR {
    private PoldiR() {}

    /** Ruft die erste passende Methode auf. names = "a|b|c". */
    public static Object call(Object target, String names, Object... args) {
        if (target == null) return null;
        Class<?> c = target instanceof Class<?> k ? k : target.getClass();
        Object inst = target instanceof Class<?> ? null : target;
        for (String n : names.split("\\|")) {
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (!m.getName().equals(n) || m.getParameterCount() != args.length) continue;
                    if (inst == null && !Modifier.isStatic(m.getModifiers())) continue;
                    if (!fits(m.getParameterTypes(), args)) continue;
                    try {
                        m.setAccessible(true);
                        return m.invoke(inst, args);
                    } catch (Throwable ignored) {}
                }
            }
            for (Method m : c.getMethods()) {
                if (!m.getName().equals(n) || m.getParameterCount() != args.length || !fits(m.getParameterTypes(), args)) continue;
                try { return m.invoke(inst, args); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /** Liest das erste vorhandene Feld. */
    public static Object field(Object target, String names) {
        if (target == null) return null;
        for (String n : names.split("\\|")) {
            for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
                try {
                    Field f = k.getDeclaredField(n);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /** Erst Methode (ohne Argumente), dann Feld. */
    public static Object get(Object target, String names) {
        Object v = call(target, names);
        return v != null ? v : field(target, names);
    }

    public static double num(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    private static boolean fits(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Object a = args[i];
            Class<?> t = wrap(types[i]);
            if (a == null) { if (types[i].isPrimitive()) return false; continue; }
            if (!t.isInstance(a)) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == double.class) return Double.class;
        if (t == float.class) return Float.class;
        if (t == boolean.class) return Boolean.class;
        if (t == byte.class) return Byte.class;
        if (t == short.class) return Short.class;
        if (t == char.class) return Character.class;
        return t;
    }
}
