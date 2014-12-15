/*****************************************************************************
    This file is part of Git-Starteam.

    Git-Starteam is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Git-Starteam is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Git-Starteam.  If not, see <http://www.gnu.org/licenses/>.
******************************************************************************/
package org.sync.githelper.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.ossnoize.git.fastimport.Blob;
import org.ossnoize.git.fastimport.Commit;
import org.ossnoize.git.fastimport.Data;
import org.ossnoize.git.fastimport.FileModification;
import org.ossnoize.git.fastimport.Sha1Ref;
import org.ossnoize.git.fastimport.enumeration.GitFileType;
import org.ossnoize.git.fastimport.exception.InvalidPathException;
import org.sync.RepositoryHelper;
import org.sync.githelper.GitHelper;
import org.sync.util.FileUtility;
import org.sync.util.LogEntry;
import org.sync.util.SmallRef;

public class GitHelperTest {
	private RepositoryHelper test;
	private File bareRepo;
	private Blob blob1;
	
	@Before
	public void setUp() throws Exception {
		test = new GitHelper(null, false, null);
		bareRepo = new File(System.getProperty("java.io.tmpdir") + File.separator + "bareRepo");
		Data content = new Data();
		content.writeData("This is the content of the file".getBytes());
		blob1 = new Blob(content);
	}

	@After
	public void tearDown() throws Exception {
		test.dispose();
		test = null;
		FileUtility.rmDir(bareRepo);
		bareRepo = null;
		blob1 = null;
	}

	@Test(timeout=1000)
	public void testGetListOfTrackedFile() {
		Set<String> listOfFiles = test.getListOfTrackedFile("master");
		assertNotNull(listOfFiles);
		// Check for self in the list of files always start from the working directory. 
		assertTrue(listOfFiles.contains("src/test/java/org/sync/githelper/test/GitHelperTest.java"));
		
		Set<String> listOfNoFiles = test.getListOfTrackedFile("Non-existing-branch");
		assertNotNull(listOfNoFiles);
		assertEquals(0, listOfNoFiles.size());
	}
	
	@Test
	public void testSpecialFiles() {
		assertTrue(test.isSpecialFile(".gitignore"));
		assertTrue(test.isSpecialFile("a/deep/down/git/directory/.gitignore"));
		assertTrue(test.isSpecialFile(".gitattributes"));
		assertTrue(test.isSpecialFile("a/deep/down/git/directory/.gitattributes"));
		assertFalse(test.isSpecialFile("aFile.txt"));
		assertFalse(test.isSpecialFile("some/random/directory/file.gitignore"));
	}

	@Test
	public void testGC() {
		assertEquals(0, test.gc());
		test.setWorkingDirectory(System.getProperty("java.io.tmpdir"), false);
		assertEquals(128, test.gc());
	}
	
	@Test
	public void testIsBare() throws IOException {
		assertEquals(false, test.isBareRepository());
		bareRepo.mkdir();
		test.setWorkingDirectory(bareRepo.getAbsolutePath(), true);
		assertEquals(true, test.isBareRepository());
	}
	
	@Test
	public void testAlternateDirectory() throws Exception {
		GitHelper helper = new GitHelper("/usr/bin", false, null);
		helper.dispose();
	}
	
	@Test
	public void testCommitInBareRepository() throws IOException, InvalidPathException {
		assertFalse(test.isFastImportRunning());
		bareRepo.mkdir();
		test.setWorkingDirectory(bareRepo.getAbsolutePath(), true);
		test.writeBlob(blob1);
		Commit testCommit = new Commit("Me Tester", "Me.Tester@domain.com", "This is a test commit", "master", new java.util.Date());
		FileModification fo = new FileModification(blob1);
		fo.setPath("test/path/of/file.txt");
		fo.setFileType(GitFileType.Normal);
		testCommit.addFileOperation(fo);
		test.writeCommit(testCommit);
		
		assertTrue(test.isFastImportRunning());
		
		Commit nextCommit = new Commit("Me Tester", "Me.Tester@domain.com", "This is a test commit #2", "master", new java.util.Date());
		FileModification fo2 = new FileModification(blob1);
		fo2.setPath("test/path/of/file - Copy.txt");
		fo2.setFileType(GitFileType.Normal);
		nextCommit.setFromCommit(testCommit);
		test.writeCommit(nextCommit);
		
		test.dispose();
		
		java.util.Date lastCommit = test.getLastCommitOfBranch("master");
		assertNotNull(lastCommit);
		System.out.println(lastCommit.getTime() + " " + System.currentTimeMillis());
		assertTrue((lastCommit.getTime() - System.currentTimeMillis()) < 2500);
	}
	
	@Test
	public void testFileRegistration() {
		bareRepo.mkdir();
		test.setWorkingDirectory(bareRepo.getAbsolutePath(), true);
		
		test.registerFileId("master", "test/path/of/file.txt", 1234, 0);
		test.registerFileId("master", "test/path/of/another/file.txt", 1235, 1);
		assertEquals(new Integer(1234), test.getRegisteredFileId("master", "test/path/of/file.txt"));
		assertEquals(new Integer(0), test.getRegisteredFileVersion("master", "test/path/of/file.txt"));
		assertEquals(new Integer(1235), test.getRegisteredFileId("master", "test/path/of/another/file.txt"));
		assertEquals(new Integer(1), test.getRegisteredFileVersion("master", "test/path/of/another/file.txt"));
		assertNull(test.getRegisteredFileId("master", "test/path/of/non/existing/file.txt"));
		assertNull(test.getRegisteredFileVersion("master", "test/path/of/non/existing/file.txt"));
		
		test.updateFileVersion("master", "test/path/of/file.txt", 3);
		assertEquals(new Integer(3), test.getRegisteredFileVersion("master", "test/path/of/file.txt"));
		
		test.unregisterFileId("master", "test/path/of/another/file.txt");
		assertNull(test.getRegisteredFileId("master", "test/path/of/another/file.txt"));
		
		assertFalse(test.updateFileVersion("master", "unexistingFile.txt", 4));
		assertFalse(test.registerFileId("master", "test/path/of/file.txt", 12356, 6));
	}

	@Ignore
	@Test
	public void testLogEntry() {
		List<LogEntry> renamedLog = test.getCommitLog(new SmallRef("e09d507"));
		assertEquals("Steve Tousignant <s.tousignant@gmail.com>", renamedLog.get(0).getAuthor());
		assertEquals("MD5Builder is better placed in the util package.", renamedLog.get(0).getComment());
		assertEquals(new Sha1Ref("e09d5071e480a2f4906dd8fae05afdbdf3492415"), renamedLog.get(0).getCommitRef());
		assertEquals(2, renamedLog.get(0).getFilesEntry().size());
		assertEquals(LogEntry.TypeOfModification.Modification, renamedLog.get(0).getFilesEntry().get(0).getTypeOfModification());
		assertEquals(LogEntry.TypeOfModification.Rename, renamedLog.get(0).getFilesEntry().get(1).getTypeOfModification());
		assertEquals("syncronizer/src/org/sync/githelper/GitHelper.java", renamedLog.get(0).getFilesEntry().get(0).getPath());
		assertEquals("syncronizer/src/org/sync/MD5Builder.java", renamedLog.get(0).getFilesEntry().get(1).getPath());
		assertEquals("syncronizer/src/org/sync/util/MD5Builder.java", renamedLog.get(0).getFilesEntry().get(1).renamedTo());
		assertFalse(renamedLog.get(0).getFilesEntry().get(1).hasTypeChange());
		assertEquals(GitFileType.Normal, renamedLog.get(0).getFilesEntry().get(1).getFromType());
		assertEquals(GitFileType.Normal, renamedLog.get(0).getFilesEntry().get(1).getToType());
		assertEquals(98, renamedLog.get(0).getFilesEntry().get(1).getDiffRatio());
	}
}
