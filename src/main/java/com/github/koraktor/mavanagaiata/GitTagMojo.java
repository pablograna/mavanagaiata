/**
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the new BSD License.
 *
 * Copyright (c) 2011-2012, Sebastian Staudt
 */

package com.github.koraktor.mavanagaiata;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

import org.apache.maven.plugin.MojoExecutionException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * This goal provides the most recent Git tag in the "mavanagaiata.tag" and
 * "mvngit.tag" properties.
 *
 * @author Sebastian Staudt
 * @goal tag
 * @phase initialize
 * @requiresProject
 * @since 0.1.0
 */
public class GitTagMojo extends AbstractGitMojo {

    private RevWalk revWalk;

    private String tag;

    private Map<RevCommit, String> tagCommits;

    /**
     * This will first read all tags and walk the commit hierarchy down from
     * HEAD until it finds one of the tags. The name of that tag is written
     * into "mavanagaiata.tag" and "mvngit.tag" respectively.
     *
     * @throws MojoExecutionException if the tags cannot be read
     */
    public void execute() throws MojoExecutionException {
        try {
            this.tag = null;
            RevCommit head = this.getHead();
            this.revWalk = new RevWalk(this.repository);
            Map<String, Ref> tags = this.repository.getTags();
            this.tagCommits = new HashMap<RevCommit, String>();

            for(Map.Entry<String, Ref> tag : tags.entrySet()) {
                try {
                    RevTag revTag = this.revWalk.parseTag(tag.getValue().getObjectId());
                    RevObject object = this.revWalk.peel(revTag);
                    if(!(object instanceof RevCommit)) {
                        continue;
                    }
                    this.tagCommits.put((RevCommit) object, tag.getKey());
                } catch(IncorrectObjectTypeException e) {
                    continue;
                }
            }

            int distance = -1;

            String abbrevId = this.repository.getObjectDatabase().newReader()
                .abbreviate(head).name();

            if(this.tagCommits.isEmpty() ||
               (distance = this.findNearestTag()) < 0) {
                this.addProperty("tag.describe", abbrevId);
                this.addProperty("tag.name", "");
            } else {
                this.addProperty("tag.name", this.tag);
                if(distance == 0) {
                    this.addProperty("tag.describe", this.tag);
                } else {
                    this.addProperty("tag.describe", this.tag + "-" + distance + "-g" + abbrevId);
                }
            }
        } catch(IOException e) {
            throw new MojoExecutionException("Unable to read Git tag", e);
        }
    }

    /** Finds the 'closest' tag from head.
     * 
     * This performs a breadth first search in the commit tree from head, and
     * updates the value of this.tag with the fist tag found. This is not
     * exactly as what git describe does, but it is close enough.
     * 
     * This is much faster than the old recursive implementation because it
     * considers much less commits.
     * 
     * @return The distance at which the tag has been found, or <code>-1</code>
     *         if no tag is reachable from the given commit.
     * @throws IOException
     */
    private int findNearestTag() throws IOException {
	int currentDistance = -1;
	
	// Breadth-first search. We start with the head as the first node.
	List<RevCommit> thisLevelCommits = new LinkedList<RevCommit>();
	thisLevelCommits.add(this.getHead());
	// The next list of 'siblings' to walk.
	List<RevCommit> nextLevelCommits = new LinkedList<RevCommit>();
	HashSet<String> seen = new HashSet<String>();
	while (!thisLevelCommits.isEmpty()) {
	    for (RevCommit revCommit : thisLevelCommits) {
		currentDistance++;

		String commitId = revCommit.getId().toString();
		if (seen.contains(commitId)) {
		    // Do not process seen commits again.
		    continue;
		}
		seen.add(commitId);

		RevCommit commit = (RevCommit) this.revWalk.peel(revCommit);

		// Side effect: this sets the tag field if we found a commit.
		isTagged(commit);
		
		RevCommit[] nextCommits = commit.getParents();
		if (nextCommits != null) {
		    nextLevelCommits.addAll(Arrays.asList(nextCommits));
		}
	    }
	    if (this.tag == null) {
		thisLevelCommits = nextLevelCommits;
	    } else {
		thisLevelCommits = new LinkedList<RevCommit>();
	    }
	    nextLevelCommits = new LinkedList<RevCommit>();
	}

	if (this.tag == null) {
	    // We did not find the tag, just return -1.
	    return -1;
	} else {
	    return currentDistance;
	}
    }

    /**
     * Returns whether a specific commit has been tagged.
     *
     * If the commit is tagged, the tag's name is saved as property "tag"
     *
     * @param commit The commit to check
     * @see #addProperty(String, String)
     * @return <code>true</code> if this commit has been tagged
     */
    private boolean isTagged(RevCommit commit) {
        if(this.tagCommits.containsKey(commit)) {
            this.tag = this.tagCommits.get(commit);
            return true;
        }

        return false;
    }
}
