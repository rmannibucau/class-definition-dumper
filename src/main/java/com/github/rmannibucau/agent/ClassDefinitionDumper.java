package com.github.rmannibucau.agent;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassDefinitionDumper {
    private ClassDefinitionDumper() {
        // no-op
    }

    public static void premain(final String agentArgs, final Instrumentation instrumentation) {
        agentmain(agentArgs, instrumentation);
    }

    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) {
        final Map<String, String> args = parse(agentArgs);
        final File target = new File(args.getOrDefault("output", new File(System.getProperty("java.io.tmpdir"), ClassDefinitionDumper.class.getSimpleName()).getAbsolutePath()));
        final boolean dumpClasses = Boolean.parseBoolean(args.getOrDefault("binary", "true"));
        final boolean dumpMeta = Boolean.parseBoolean(args.getOrDefault("meta", "true"));
        final Collection<String> includes = Stream.of(args.getOrDefault("includes", "").split(","))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .collect(Collectors.toSet());
        instrumentation.addTransformer((loader, className, classBeingRedefined, protectionDomain, buffer) -> {
            if (className != null && !includes.isEmpty() && includes.stream().noneMatch(className::startsWith)) {
                return buffer;
            }
            dump(target, loader, className, protectionDomain, buffer, dumpClasses, dumpMeta);
            return buffer;
        });
    }

    private static Map<String, String> parse(final String agentArgs) {
        return ofNullable(agentArgs)
                .map(it -> it.split("\\|"))
                .map(it -> Stream.of(it)
                        .map(String::trim)
                        .filter(v -> !v.isEmpty())
                        .map(kp -> kp.split("="))
                        .collect(toMap(kp -> kp[0], kp -> kp[1])))
                .orElseGet(Collections::emptyMap);
    }

    private static void dump(final File root, final ClassLoader loader,
                             final String className, final ProtectionDomain protectionDomain,
                             final byte[] classfileBuffer, final boolean dumpClasses, final boolean dumpMeta) {
        if (dumpMeta) {
            final File output = new File(root, "definition/" + className);
            output.getParentFile().mkdirs();
            try (final PrintStream writer = new PrintStream(new FileOutputStream(output))) {
                final Date now = new Date();
                writer.println("Date = " + now);
                writer.println("Timestamp = " + now.getTime());
                writer.println("Name = " + className);
                writer.println("Location = " +
                        (protectionDomain == null || protectionDomain.getCodeSource() == null ? null : protectionDomain.getCodeSource().getLocation()));
                writer.println("Loader = " + loader);
                writer.print("Stack = ");
                new Exception("Stack debug exception").printStackTrace(writer);
            } catch (final FileNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        if (dumpClasses) {
            final File clazz = new File(root, "classes/" + className + ".class");
            clazz.getParentFile().mkdirs();
            try (final OutputStream writer = new BufferedOutputStream(new FileOutputStream(clazz))) {
                writer.write(classfileBuffer);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
