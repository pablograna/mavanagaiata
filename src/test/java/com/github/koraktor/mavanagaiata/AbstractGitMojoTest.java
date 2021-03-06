/**
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the new BSD License.
 *
 * Copyright (c) 2011-2012, Sebastian Staudt
 */

package com.github.koraktor.mavanagaiata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import org.eclipse.jgit.revwalk.RevCommit;

import org.junit.Before;
import org.junit.Test;

public class AbstractGitMojoTest extends AbstractMojoTest<AbstractGitMojo> {

    @Before
    public void setUp() throws Exception {
        this.mojo = new AbstractGitMojo() {
            public void execute()
                    throws MojoExecutionException, MojoFailureException {}
        };

        super.setUp();
    }

    @Test
    public void testDirs() {
        assertNotNull(this.mojo.project);
        assertEquals(new File("src/test/resources/test-project").getAbsoluteFile(), this.mojo.project.getBasedir());
    }

    @Test
    public void testErrors() {
        this.mojo.gitDir = null;
        try {
            this.mojo.initRepository();
            fail("No exception thrown");
        } catch(Exception e) {
            assertEquals(FileNotFoundException.class, e.getClass());
            assertEquals("Git directory is not set", e.getMessage());
        }

        String home = System.getenv().get("HOME");
        if (home == null) {
            home = System.getenv().get("HOMEDRIVE") + System.getenv("HOMEPATH");
        }
        this.mojo.gitDir = new File(home).getAbsoluteFile();
        try {
            this.mojo.initRepository();
            fail("No exception thrown");
        } catch(Exception e) {
            assertEquals(FileNotFoundException.class, e.getClass());
            assertEquals(this.mojo.gitDir + " is not inside a Git repository", e.getMessage());
        }

        this.mojo.gitDir = new File("src/test/resources/non-existant-project/_git");
        try {
            this.mojo.initRepository();
            fail("No exception thrown");
        } catch(Exception e) {
            assertEquals(FileNotFoundException.class, e.getClass());
            assertEquals(this.mojo.gitDir + " does not exist", e.getMessage());
        }

        this.mojo.gitDir = new File("src/test/resources/broken-project/_git");
        try {
            this.mojo.initRepository();
            fail("No exception thrown");
        } catch(Exception e) {
            assertEquals(IOException.class, e.getClass());
            assertEquals("Unknown repository format \"42\"; expected \"0\".", e.getMessage());
        }
    }

    @Test
    public void testInitRepository() throws IOException {
        this.mojo.initRepository();
        assertNotNull(this.mojo.repository);
        assertEquals(new File("src/test/resources/test-project/_git").getAbsolutePath(),
            this.mojo.repository.getDirectory().getAbsolutePath());
    }

    @Test
    public void testGetHead() throws IOException, MojoExecutionException {
        RevCommit head = this.mojo.getHead();
        assertEquals(this.headId, head.getName());

        this.mojo.initRepository();

        head = this.mojo.getHead();
        assertEquals(this.headId, head.getName());
    }

    @Test
    public void testRelativeHead() throws IOException, MojoExecutionException {
        String head;

        this.mojo.head = "HEAD^";
        head = this.mojo.getHead().getName();
        assertEquals("f391f31093fd200534a4fb2e517af89efbdc5fe5", head);

        this.mojo.head = "HEAD~3";
        head = this.mojo.getHead().getName();
        assertEquals("d50fdcd2858ac9531d6dd87c1de3b623fa243204", head);

        this.mojo.head = "HEAD^~2^";
        head = this.mojo.getHead().getName();
        assertEquals("0e7d0435e30d0f726d62ccadd202c9240df56019", head);
    }

    @Test
    public void testAddProperty() {
        Properties properties = this.mojo.project.getProperties();

        this.mojo.addProperty("name", "value");

        this.assertProperty("value", "name");

        this.mojo.propertyPrefixes = new String[] { "prefix" };
        this.mojo.addProperty("prefixed", "value");

        this.assertProperty("value", "prefixed");
        assertNull(properties.get("mavanagaiata.prefixed"));
        assertNull(properties.get("mvngit.prefixed"));
    }

}
