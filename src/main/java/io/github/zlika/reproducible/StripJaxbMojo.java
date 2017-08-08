/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zlika.reproducible;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Normalizes ObjectFactory java files generated by the JAXB xjc tool.
 */
@Mojo(name = "strip-jaxb", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public final class StripJaxbMojo extends AbstractMojo
{
    /**
     * The file encoding to use when reading the source files.
     * If the property project.build.sourceEncoding is not set,
     * the platform default encoding is used.
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String encoding;
    
    /**
     * Directory where to find the source files generated by xjc.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources",
            property = "reproducible.generatedDirectory", required = true)
    private File generatedDirectory;
    
    /**
     * If true, skips the execution of the goal.
     */
    @Parameter(defaultValue = "false", property = "reproducible.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException
    {
        if (skip)
        {
            getLog().info("Skipping execution of goal \"strip-jaxb\"");
        }
        else
        {
            fix();
        }
    }
    
    private void fix() throws MojoExecutionException
    {
        if (!generatedDirectory.exists() || !generatedDirectory.isDirectory())
        {
            return;
        }
        final Charset charset = Charset.forName(encoding);
        final JaxbObjectFactoryFixer fixer = new JaxbObjectFactoryFixer(charset);
        final File tmpFile = createTempFile();
        
        try
        {
            Files.walk(generatedDirectory.toPath())
                .filter(Files::isRegularFile)
                .filter(f -> "ObjectFactory.java".equals(f.toFile().getName()))
                .forEach(f ->
                {
                    getLog().info("Stripping " + f.toFile().getAbsolutePath());
                    try
                    {
                        fixer.strip(f.toFile(), tmpFile);
                        Files.move(tmpFile.toPath(), f, StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (IOException e)
                    {
                        getLog().error("Error when normalizing " + f.toFile().getAbsolutePath(), e);
                    }
                });
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Error when visiting " + generatedDirectory.getAbsolutePath(), e);
        }
    }

    private File createTempFile() throws MojoExecutionException
    {
        try
        {
            final File out = File.createTempFile("ObjectFactory", null);
            out.deleteOnExit();
            return out;
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Cannot create temp file", e);
        }
    }
}