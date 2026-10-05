import org.gradle.api.file.FileSystemOperations;

import javax.inject.Inject;

/** Injected file operations for isolated runtime fixtures, without execution-time Project access. */
public abstract class FixtureFileOperations {
    @Inject
    public abstract FileSystemOperations getFiles();
}
