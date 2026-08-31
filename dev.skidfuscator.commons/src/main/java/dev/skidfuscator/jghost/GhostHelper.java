package dev.skidfuscator.jghost;

import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.gson.JsonSyntaxException;
import dev.skidfuscator.jghost.tree.GhostClassNode;
import dev.skidfuscator.jghost.tree.GhostContents;
import dev.skidfuscator.jghost.tree.GhostLibrary;
import dev.skidfuscator.logger.Logger;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;
import lombok.experimental.UtilityClass;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.asm.ClassHelper;
import org.mapleir.asm.ClassNode;
import org.topdank.byteengineer.commons.data.JarClassData;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.AbstractJarDownloader;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.in.SingleJmodDownloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.stream.Collectors;

@UtilityClass
public class GhostHelper {
    public ApplicationClassSource getLibraryClassSource(final SkidfuscatorSession session, final Logger logger, final File file) {
        return getLibraryClassSource(session, logger, file, false);
    }

    public ApplicationClassSource getJvm(final SkidfuscatorSession session, final Logger logger, final File file) {
        return getLibraryClassSource(session, logger, file, true);
    }

    public ApplicationClassSource getJvm(final Logger logger, final boolean fuckit, final File file) {
        return getLibraryClassSource(logger, fuckit, file, true);
    }

    public ApplicationClassSource getJvm(final Logger logger, final boolean fuckit, final File file, final File mappings) {
        return getLibraryClassSource(logger, fuckit, file, mappings, true);
    }

    public GhostLibrary getLibrary(final Logger logger, final File lib, boolean jre) {
        return getLibrary(logger, lib, null, jre);
    }

    public GhostLibrary getLibrary(final Logger logger, final File lib, final File folder, boolean jre) {
        logger.post("[+] " + lib.getAbsolutePath());

        final StringBuilder outputPath = new StringBuilder();
        if (folder != null) {
            outputPath.append(folder.getAbsolutePath()).append("/");
        } else {
            outputPath.append("mappings/");
        }

        if (jre) {
            outputPath.append("jvm/");
        }

        outputPath.append(lib.getName());
        outputPath.append(".json");

        final File output = new File(outputPath.toString());

        final GhostLibrary library;

        if (!output.exists()) {
            logger.post("[?] Could not find mappings for " + lib.getAbsolutePath() + "... Creating...");
            output.getParentFile().mkdirs();
            library = GhostHelper.createFromLibraryFile(logger, lib);
            GhostHelper.saveLibraryFile(logger, library, output);
            logger.post("[✓] Creating mappings for " + lib.getAbsolutePath() + "!");
        } else {
            library = GhostHelper.readFromLibraryFile(logger, output);
            if (library == null || !GhostHelper.matchesLibraryHash(logger, lib, library)) {
                logger.post("[?] Mappings cache for " + lib.getAbsolutePath() + " is corrupt or outdated, recreating...");
                library = GhostHelper.createFromLibraryFile(logger, lib);
                GhostHelper.saveLibraryFile(logger, library, output);
                logger.post("[✓] Recreated mappings for " + lib.getAbsolutePath() + "!");
            }
        }

        return library;
    }

    public ApplicationClassSource getLibraryClassSource(final SkidfuscatorSession session, final Logger logger, final File lib, boolean jvm) {
        return importFile(logger, session.isFuckIt(), getLibrary(logger, lib, jvm));
    }

    public ApplicationClassSource getLibraryClassSource(final Logger logger, final boolean fuckIt, final File lib, boolean jvm) {
        return importFile(logger, fuckIt, getLibrary(logger, lib, jvm));
    }

    public ApplicationClassSource getLibraryClassSource(final SkidfuscatorSession session, final Logger logger, final File lib, final File mappings, final boolean jvm) {
        return importFile(logger, session.isFuckIt(), getLibrary(logger, lib, mappings, jvm));
    }

    public ApplicationClassSource getLibraryClassSource(final Logger logger, final boolean fuckit, final File lib, final File mappings, final boolean jvm) {
        return importFile(logger, fuckit, getLibrary(logger, lib, mappings, jvm));
    }

    public ApplicationClassSource importFile(final Logger logger, final boolean fuckit, final GhostLibrary library) {
        if (library == null || library.getContents() == null || library.getContents().getClasses() == null) {
            logger.error("Failed to import library: cache file is missing or corrupt");
            return new ApplicationClassSource("empty", fuckit, Collections.emptyList());
        }

        /* Create a new library class source with superior to default priority */
        final ApplicationClassSource libraryClassSource = new ApplicationClassSource(
                library.getName(),
                fuckit,
                library.getContents()
                        .getClasses()
                        .values()
                        .stream()
                        .map(e -> ClassHelper.create(e.read())).collect(Collectors.toList())
        );
        logger.post("[✓] Imported " + library.getContents().getClasses().size() + " library classes...");

        return libraryClassSource;
    }

    public GhostLibrary readFromLibraryFile(final Logger logger, final File file) {
        try (final Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return Ghost.gson().fromJson(reader, GhostLibrary.class);
        } catch (IOException e) {
            logger.error("Failed to read library cache: " + file.getAbsolutePath(), e);
            return null;
        } catch (JsonSyntaxException e) {
            logger.error("Corrupt library cache: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private boolean matchesLibraryHash(final Logger logger, final File lib, final GhostLibrary library) {
        if (library.getSha256() == null) {
            return false;
        }

        try {
            final String sha256 = Files.asByteSource(lib).hash(Hashing.sha256()).toString();
            return sha256.equals(library.getSha256());
        } catch (IOException e) {
            logger.error("Failed to verify library hash for " + lib.getAbsolutePath(), e);
            return false;
        }
    }

    public GhostLibrary createFromLibraryFile(final Logger logger, final File file) {
        final JarInfo jarInfo = new JarInfo(file);
        final AbstractJarDownloader<ClassNode> downloader = file.getName().endsWith(".jmod")
                        ? new SingleJmodDownloader<>(jarInfo)
                        : new SingleJarDownloader<>(jarInfo);

        try {
            downloader.download();
        } catch (IOException e) {
            logger.error("Failed to download library", e);
            return null;
        }

        final GhostContents ghostContents = new GhostContents();
        final GhostLibrary ghostLibrary = new GhostLibrary();
        ghostLibrary.setName(file.getName());
        ghostLibrary.setContents(ghostContents);

        try {
            final ByteSource byteSource = Files.asByteSource(file);
            ghostLibrary.setMd5(byteSource.hash(Hashing.md5()).toString());
            ghostLibrary.setSha1(byteSource.hash(Hashing.sha1()).toString());
            ghostLibrary.setSha256(byteSource.hash(Hashing.sha256()).toString());
        } catch (Throwable e) {
            logger.error("Failed to hash library", e);
            return null;
        }

        for (JarClassData classContent : downloader.getJarContents().getClassContents()) {
            final GhostClassNode ghostClassNode = GhostClassNode.of(classContent.getClassNode().node);
            ghostContents.getClasses().put(classContent.getName(), ghostClassNode);
        }

        return ghostLibrary;
    }

    public void saveLibraryFile(final Logger logger, final GhostLibrary library, final File file) {
        if (library == null) {
            return;
        }

        final File temp = new File(file.getAbsolutePath() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            try (final Writer writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8))) {
                writer.write(Ghost.gson().toJson(library, GhostLibrary.class));
            }
            java.nio.file.Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to save library cache: " + file.getAbsolutePath(), e);
            temp.delete();
        }
    }

    public byte[] serializeLibraryFile(final GhostLibrary library) {
        return Ghost.gson().toJson(library, GhostLibrary.class).getBytes(StandardCharsets.UTF_8);
    }
}
