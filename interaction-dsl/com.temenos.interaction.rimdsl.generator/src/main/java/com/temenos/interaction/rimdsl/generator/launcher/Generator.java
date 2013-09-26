package com.temenos.interaction.rimdsl.generator.launcher;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.generator.JavaIoFileSystemAccess;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;

import com.google.inject.Inject;

/**
 * This class generates code from a DSL model.
 * @author aphethean
 *
 */
public class Generator {
	
	@Inject
    private XtextResourceSet resourceSet;
	@Inject
	private IResourceValidator validator;
	@Inject
	private IGenerator generator;
	@Inject
	private JavaIoFileSystemAccess fileAccess;

	public boolean runGeneratorDir(String inputDirPath, String outputPath) {
		List<String> files = getFiles(inputDirPath, ".rim");
		for (String modelPath : files) {
			resourceSet.getResources().add(resourceSet.getResource(URI.createFileURI(modelPath), true));
		}
		boolean result = false;
		for (String modelPath : files) {
			result = runGenerator(modelPath, outputPath);
		}
		return result;
	}
	
	protected String toSystemFileName(String fileName) {
		return fileName.replace("/", File.separator);
	}

	/**
	 * @param path a folder path
	 * @param extension a file extension
	 * @return a list of files contained in the specified folder and
	 * 		its sub folders filtered by extension
	 */
	protected ArrayList<String> getFiles(String path, String extension) {
		ArrayList<String> result = new ArrayList<String>();
		
		path = toSystemFileName(path);
		getFilesRecursively(path, result, extension);
		
		return result;
	}

	private void getFilesRecursively(String path, ArrayList<String> result, String extension) {
		File file = new File(path);
		if (file.isDirectory()) {
//			if (!file.getName().equals(".svn")) {
				String[] contents = file.list();
				for (String sub : contents) {
					getFilesRecursively(path+File.separator+sub, result, extension);
				}
//			}
		}
		else {
			if (file.getName().endsWith(extension))
				result.add(file.getAbsolutePath());
		}
	}
	
	public boolean runGenerator(String inputPath, String outputPath) {
		
		// load the resource
		resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
		Resource resource = resourceSet.getResource(URI.createFileURI(inputPath), true);

		// validate the resource
		List<Issue> list = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
		if (!list.isEmpty()) {
			for (Issue issue : list) {
				System.err.println(issue);
			}
			return false;
		}

		// configure and start the generator
		fileAccess.setOutputPath(outputPath);
		generator.doGenerate(resource, fileAccess);
		
		return true;
	}
}