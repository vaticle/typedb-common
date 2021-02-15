/*
 * Copyright (C) 2021 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.common.test.assembly;

import org.apache.commons.io.FileUtils;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.fail;

public abstract class GraknRunner {

    private static final String TAR_GZ = ".tar.gz";
    private static final String TGZ = ".tgz";
    private static final String ZIP = ".zip";

    protected final Path graknPath;
    protected ProcessExecutor executor;

    public GraknRunner() throws InterruptedException, TimeoutException, IOException {
        this(Objects.requireNonNull(distributionArchive()));
    }

    public GraknRunner(File distributionArchive) throws InterruptedException, TimeoutException, IOException {
        System.out.println("Constructing a " + name() + " runner");

        if (!distributionArchive.exists()) {
            throw new IllegalArgumentException("Grakn distribution file is missing from " + distributionArchive.getAbsolutePath());
        }

        checkAndDeleteExistingDistribution(distributionArchive);

        String distributionArchiveFormat = distributionFormat(distributionArchive);
        Path distributionDir = distributionTarget(distributionArchive);
        executor = new ProcessExecutor()
                .directory(Paths.get(".").toAbsolutePath().toFile())
                .redirectOutput(System.out)
                .redirectError(System.err)
                .readOutput(true)
                .destroyOnExit();
        graknPath = distributionSetup(distributionDir, distributionArchiveFormat, distributionArchive);
        System.out.println(name() + " runner constructed");
    }

    private String distributionFormat(File distributionFile) {
        if (distributionFile.toString().endsWith(TAR_GZ)) {
            return TAR_GZ;
        } else if (distributionFile.toString().endsWith(TGZ)) {
                return TGZ;
        } else if (distributionFile.toString().endsWith(ZIP)) {
            return ZIP;
        } else {
            fail(String.format("Distribution file format should either be %s, %s or %s", TAR_GZ, TGZ, ZIP));
        }
        return "";
    }

    private Path distributionTarget(File distributionFile) {
        String format = distributionFormat(distributionFile);
        return Paths.get(distributionFile.toString().replaceAll(
                format.replace(".", "\\."), ""
        ));
    }

    private void checkAndDeleteExistingDistribution(File distributionFile) throws IOException {
        Path target = distributionTarget(distributionFile);
        System.out.println("Checking for existing distribution at " + target.toAbsolutePath().toString());
        if (target.toFile().exists()) {
            System.out.println("An existing distribution found. Deleting...");
            FileUtils.deleteDirectory(target.toFile());
            System.out.println("Existing distribution deleted");
        } else {
            System.out.println("There is no existing distribution");
        }
    }

    private Path distributionSetup(Path distributionDir, String distributionArchiveFormat, File distributionArchive) throws IOException, TimeoutException, InterruptedException {
        System.out.println("Unarchiving " + name() + " distribution");
        Files.createDirectories(distributionDir);
        if (distributionArchiveFormat.equals(TAR_GZ) || distributionArchiveFormat.equals(TGZ)) {
            executor.command("tar", "-xf", distributionArchive.toString(),
                    "-C", distributionDir.toString()).execute();
        } else {
            executor.command("unzip", "-q", distributionArchive.toString(),
                    "-d", distributionDir.toString()).execute();
        }
        // The Grakn Cluster archive extracts to a folder inside GRAKN_TARGET_DIRECTORY named
        // grakn-core-server-{platform}-{version}. We know it's the only folder, so we can retrieve it using Files.list.
        final Path graknPath = Files.list(distributionDir).findFirst().get();
        executor = executor.directory(graknPath.toFile());
        System.out.println(name() + " distribution unarchived");
        return graknPath;
    }

    protected List<String> getGraknBinary() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? Arrays.asList("cmd.exe", "/c", "grakn.bat") : Collections.singletonList("grakn");
    }

    protected static File distributionArchive() {
        String[] args = System.getProperty("sun.java.command").split(" ");
        return Objects.requireNonNull(args.length > 1 ? new File(args[1]) : null);
    }

    abstract String name();
}
