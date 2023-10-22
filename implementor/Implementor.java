package info.kgeorgiy.ja.shpraidun.implementor;

import info.kgeorgiy.java.advanced.implementor.JarImpler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;


/**
 * Implement interface specified by provided {@code token}.
 */
public class Implementor implements JarImpler {
    private static final String NEW_LINE = System.lineSeparator();
    private static final String TAB = "    ";

    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkToken(token);
        try {
            Path file = getOutputFile(token, root);
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file)) {
                addPackages(writer, token.getPackageName());
                addClass(writer, token);
            } catch (IOException e) {
                throw new ImplerException("Error while write in file", e);
            }
        } catch (IOException | SecurityException | InvalidPathException e) {
            throw new ImplerException("Can't generate class", e);
        }
    }

    /**
     * Check if {@code token} is implementable.
     * {@code token} is implementable if it is not null and public interface.
     *
     * @param token token to check
     * @throws ImplerException if token is not implementable
     */
    private void checkToken(Class<?> token) throws ImplerException {
        if (token == null) {
            throw new ImplerException("Token is null");
        }
        if (!token.isInterface() || Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Cannot implement " + token.getSimpleName());
        }
    }

    /**
     * Returns the path to the java file in the {@code root} directory
     * with the name {@link #getName(Class)} of {@code token}
     *
     * @param token class token of implemented interface
     * @param root the path to the output file directory
     * @return path to file
     */
    private Path getOutputFile(Class<?> token, Path root) {
        return Path.of(root + File.separator + getName(token) + ".java");
    }

    /**
     * Returns target class name.
     * The class name is the {@link Class#getPackageName()} of {@code token} with separators instead of dots,
     * then {@link Class#getSimpleName()} of {@code token}
     * and {@code "Impl"} in the end.
     *
     * @param token class token of implemented interface
     * @return target class name
     */
    private String getName(Class<?> token) {
        return token.getPackageName().replace(".", File.separator) +
                File.separator + token.getSimpleName() + "Impl";
    }

    /**
     * Writes package for the implementing interface if it exists otherwise it does nothing
     *
     * @param writer BufferedWriter which write class
     * @param packageName String name of package
     * @throws IOException error with writing {@link BufferedWriter#write(String)}
     */
    private void addPackages(BufferedWriter writer, String packageName) throws IOException {
        writer.write(packageName.isEmpty() ? "" : "package " + packageName + ";" + NEW_LINE);
    }

    /**
     * Writes head of class for the implementing class and then for all not static and not default method
     * add by using {@link #addMethod(BufferedWriter, Method)} and close code of class.
     *
     * @param writer BufferedWriter which write class
     * @param token class token of implemented interface
     * @throws IOException error with writing {@link BufferedWriter#write(String)}
     */
    private void addClass(BufferedWriter writer, Class<?> token) throws IOException {
        writer.write("public class " + token.getSimpleName() + "Impl implements " + token.getCanonicalName() + "{" + NEW_LINE);
        for (Method method : token.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && !method.isDefault()) {
                addMethod(writer, method);
            }
        }
        writer.write(NEW_LINE + "}" + NEW_LINE);
    }

    /**
     * Writes the passed method of the implemented interface.
     *
     * @param writer BufferedWriter which write class
     * @param method Method implemented method
     * @throws IOException error with writing {@link BufferedWriter#write(String)}
     */
    private void addMethod(BufferedWriter writer, Method method) throws IOException {
        writer.write(
                TAB + Modifier.toString(
                        method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT)
                        + " " + method.getReturnType().getTypeName()
                        + " " + method.getName()
        );
        addParameters(writer, method.getParameters());
        addThrows(writer, method.getExceptionTypes());
        writer.write(" {" + NEW_LINE);
        addReturn(writer, method.getReturnType());
        writer.write(NEW_LINE + TAB + "}" + NEW_LINE + NEW_LINE);
    }

    /**
     * Writes passed parameters in parentheses with their types and generated names separated by commas.
     *
     * @param writer BufferedWriter which write class
     * @param parameters Parameter[] with parameters we need to add
     * @throws IOException error with writing {@link BufferedWriter#write(String)}
     */
    private void addParameters(BufferedWriter writer, Parameter[] parameters) throws IOException {
        writer.write(
                "(" +
                        Arrays.stream(parameters)
                                .map(p -> p.getType().getCanonicalName() + " " + p.getName())
                                .collect(Collectors.joining(", "))
                        + ")"
        );
    }

    /**
     * Writes {@code "throws"} and passed throw exceptions separated by commas.
     *
     * @param writer BufferedWriter which write class
     * @param exceptionTypes Class[] that contains types of throw exceptions
     * @throws IOException error with writing {@link BufferedWriter#write(String)}
     */
    private void addThrows(BufferedWriter writer, Class<?>[] exceptionTypes) throws IOException {
        if (exceptionTypes.length != 0) {
            writer.write("throws " +
                    Arrays.stream(exceptionTypes)
                            .map(Class::getName)
                            .collect(Collectors.joining(", "))
            );
        }
    }

    /**
     * Writes {@code "return"} and default value base on class returned.
     *
     * @param writer BufferedWriter which write class
     * @param returned Class you need to return default value
     * @throws IOException error with writing {@link BufferedWriter#write(String)}
     */
    private void addReturn(BufferedWriter writer, Class<?> returned) throws IOException {
        writer.write(TAB + TAB + "return " + getReturnValue(returned) + ";");
    }

    /**
     * Return default value of passed class.
     * Default value for boolean is {@code "true"}
     * for void is {@code ""}
     * for primitive type is {@code "0"}
     * for the rest {@code "null"}
     *
     * @param returned Class you need to return default value
     * @return default value at String
     */
    private String getReturnValue(Class<?> returned) {
        if (returned.equals(boolean.class)) {
            return "true";
        } else if (returned.equals(void.class)) {
            return "";
        } else if(returned.isPrimitive()) {
            return "0";
        } else {
            return "null";
        }
    }

    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Path dir;
        try {
            dir = Files.createTempDirectory(jarFile.getParent(), "temp");
        } catch (IOException e) {
            throw new ImplerException("Unable to create build directory", e);
        }
        dir.toFile().deleteOnExit();
        implement(token, dir);
        compile(token, dir);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Path classFile = Path.of(getName(token) + ".class");
        try (var stream = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            stream.putNextEntry(new ZipEntry(classFile.toString().replace(File.separatorChar, '/')));
            Files.copy(dir.resolve(classFile), stream);
        } catch (IOException e) {
            throw new ImplerException("Error while writing to jar", e);
        }
    }

    /**
     * Compiles java code generated by {@link #implement(Class, Path)}.
     *
     * @param token class token of implemented interface
     * @param dir directory containing the class sources
     * @throws ImplerException if java compiler cannot be found or compilation fails
     */
    private void compile(Class<?> token, Path dir) throws ImplerException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Compiler is null");
        }
        Path classpath;
        try {
            classpath = Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new ImplerException("Uri error: ", e);
        }
        int errorCode = compiler.run(null, null, null,
                "-encoding", "UTF-8",
                "-cp",
                String.valueOf(classpath),
                String.valueOf(getOutputFile(token, dir)));
        if (errorCode != 0) {
            throw new ImplerException("Error of compilation generated class with code" + errorCode);
        }
    }

    /**
     * Wraps JarImplementor for console working.
     * If first argument {@code "-jar"} using {@link #implementJar(Class, Path)}
     * Else using {@link #implement(Class, Path)}
     * Token and path are next arguments
     *
     * @param args console arguments
     */
    public static void main(String[] args) {
        if (args.length == 0 || args[0] == null || args.length != (args[0].equals("-jar") ? 3 : 2)) {
            System.err.println("java Implementor [-jar] <class> <path>");
            return;
        }
        Implementor implementor = new Implementor();
        try {
            if (args[0].equals("-jar")) {
                implementor.implementJar(Class.forName(args[1]), Path.of(args[2]));
            } else {
                implementor.implement(Class.forName(args[0]), Path.of(args[1]));
            }
        } catch (ImplerException e) {
            System.err.println(e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Class not found " + e.getMessage());
        } catch (InvalidPathException e) {
            System.err.println("Invalid path" + e.getMessage());
        }
    }
}