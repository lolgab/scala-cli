package scala.build.internal.resource

import scala.build.Build
import scala.build.internal.Constants

object ResourceMapper {

  private def resourceMapping(build: Build.Successful): Map[os.Path, os.RelPath] = {
    val seq = for {
      resourceDirPath  <- build.sources.resourceDirs.filter(os.exists(_))
      resourceFilePath <- os.walk(resourceDirPath).filter(os.isFile(_))
      relativeResourcePath = resourceFilePath.relativeTo(resourceDirPath)
      // dismiss files generated by scala-cli
      if !relativeResourcePath.startsWith(os.rel / Constants.workspaceDirName)
    } yield (resourceFilePath, relativeResourcePath)

    seq.toMap
  }

  def copyResourcesToDirWithMapping(
    output: os.Path,
    registryFilePath: os.Path,
    newMapping: Map[os.Path, os.RelPath]
  ): Unit = {

    val oldRegistry =
      if (os.exists(registryFilePath))
        os.read(registryFilePath)
          .linesIterator
          .filter(_.nonEmpty)
          .map(os.RelPath(_))
          .toSet
      else
        Set.empty
    val removedFiles = oldRegistry -- newMapping.values

    for (f <- removedFiles)
      os.remove(output / f)

    for ((inputPath, outputPath) <- newMapping)
      os.copy(
        inputPath,
        output / outputPath,
        replaceExisting = true,
        createFolders = true
      )

    if (newMapping.isEmpty)
      os.remove(registryFilePath)
    else
      os.write.over(
        registryFilePath,
        newMapping.map(_._2.toString).toVector.sorted.mkString("\n")
      )
  }

  /** Copies and maps resources from their original path to the destination path in build output,
    * also caching output paths in a file.
    *
    * Remembering the mapping this way allows for the resource to be removed if the original file is
    * renamed/deleted.
    */
  def copyResourceToClassesDir(build: Build): Unit = build match {
    case b: Build.Successful =>
      val fullFilePath = Build.resourcesRegistry(b.inputs.workspace, b.inputs.projectName, b.scope)
      copyResourcesToDirWithMapping(b.output, fullFilePath, resourceMapping(b))
    case _ =>
  }
}