/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.jenkins.results.parser;

import com.liferay.jenkins.results.parser.JenkinsResultsParserUtil.HttpRequestMethod;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author Michael Hashimoto
 */
public class PullRequest {

	public PullRequest(String htmlURL) {
		this(htmlURL, _TEST_SUITE_NAME_DEFAULT);
	}

	public PullRequest(String htmlURL, String testSuiteName) {
		if ((testSuiteName == null) || testSuiteName.isEmpty()) {
			testSuiteName = _TEST_SUITE_NAME_DEFAULT;
		}

		_testSuiteName = testSuiteName;

		Matcher matcher = _htmlURLPattern.matcher(htmlURL);

		if (!matcher.find()) {
			throw new RuntimeException("Invalid URL " + htmlURL);
		}

		_number = Integer.parseInt(matcher.group("number"));
		_repositoryName = matcher.group("repository");
		_ownerUsername = matcher.group("owner");

		refresh();
	}

	public boolean addLabel(Label label) {
		if ((label == null) || hasLabel(label.getName())) {
			return true;
		}

		GitHubRemoteRepository gitHubRemoteRepository = getRepository();

		Label repositoryLabel = gitHubRemoteRepository.getLabel(
			label.getName());

		if (repositoryLabel == null) {
			System.out.println(
				JenkinsResultsParserUtil.combine(
					"Label ", label.getName(), " does not exist in ",
					getRepositoryName()));

			return false;
		}

		JSONArray jsonArray = new JSONArray();

		jsonArray.put(label.getName());

		String url = JenkinsResultsParserUtil.getGitHubApiUrl(
			getRepositoryName(), getOwnerUsername(),
			"issues/" + getNumber() + "/labels");

		try {
			JenkinsResultsParserUtil.toString(url, jsonArray.toString());
		}
		catch (IOException ioe) {
			System.out.println("Unable to add label " + label.getName());

			ioe.printStackTrace();

			return false;
		}

		return true;
	}

	public Commit getCommit() {
		String gitHubUserName = getOwnerUsername();
		String repositoryName = getRepositoryName();
		String sha = getSenderSHA();

		return CommitFactory.newCommit(gitHubUserName, repositoryName, sha);
	}

	public String getHtmlURL() {
		return _jsonObject.getString("html_url");
	}

	public List<Label> getLabels() {
		return _labels;
	}

	public String getLocalSenderBranchName() {
		return JenkinsResultsParserUtil.combine(
			getSenderUsername(), "-", getNumber(), "-", getSenderBranchName());
	}

	public String getNumber() {
		return String.valueOf(_number);
	}

	public String getOwnerUsername() {
		return _ownerUsername;
	}

	public GitHubRemoteRepository getRepository() {
		if (_repository == null) {
			_repository =
				(GitHubRemoteRepository)RepositoryFactory.getRemoteRepository(
					"github.com", _repositoryName, getOwnerUsername());
		}

		return _repository;
	}

	public String getRepositoryName() {
		return _repositoryName;
	}

	public String getSenderBranchName() {
		JSONObject headJSONObject = _jsonObject.getJSONObject("head");

		return headJSONObject.getString("ref");
	}

	public String getSenderRemoteURL() {
		return JenkinsResultsParserUtil.combine(
			"git@github.com:", getSenderUsername(), "/", getRepositoryName());
	}

	public String getSenderSHA() {
		JSONObject headJSONObject = _jsonObject.getJSONObject("head");

		return headJSONObject.getString("sha");
	}

	public String getSenderUsername() {
		JSONObject headJSONObject = _jsonObject.getJSONObject("head");

		JSONObject userJSONObject = headJSONObject.getJSONObject("user");

		return userJSONObject.getString("login");
	}

	public TestSuiteStatus getTestSuiteStatus() {
		return _testSuiteStatus;
	}

	public String getUpstreamBranchName() {
		JSONObject baseJSONObject = _jsonObject.getJSONObject("base");

		return baseJSONObject.getString("ref");
	}

	public String getUpstreamBranchSHA() {
		JSONObject baseJSONObject = _jsonObject.getJSONObject("base");

		return baseJSONObject.getString("sha");
	}

	public boolean hasLabel(String labelName) {
		for (Label label : _labels) {
			if (labelName.equals(label.getName())) {
				return true;
			}
		}

		return false;
	}

	public boolean isAutoCloseCommentAvailable() {
		String path = JenkinsResultsParserUtil.combine(
			"issues/", getNumber(), "/comments?page=");

		String url = JenkinsResultsParserUtil.getGitHubApiUrl(
			getRepositoryName(), getOwnerUsername(), path);

		try {
			int i = 1;

			while (true) {
				String content = JenkinsResultsParserUtil.toString(
					url + i, false);

				if (content.contains("auto-close=\\\"false\\\"")) {
					return true;
				}

				if (content.matches("\\s*\\[\\s*\\]\\s*")) {
					break;
				}

				i++;
			}

			return false;
		}
		catch (IOException ioe) {
			throw new RuntimeException(
				"Unable to check for auto-close property in GitHub comments",
				ioe);
		}
	}

	public void refresh() {
		try {
			_jsonObject = JenkinsResultsParserUtil.toJSONObject(getURL());

			_labels.clear();

			JSONArray labelJSONArray = _jsonObject.getJSONArray("labels");

			for (int i = 0; i < labelJSONArray.length(); i++) {
				JSONObject labelJSONObject = labelJSONArray.getJSONObject(i);

				_labels.add(new Label(labelJSONObject, getRepository()));
			}
		}
		catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public void removeLabel(String labelName) {
		if (!hasLabel(labelName)) {
			return;
		}

		String path = JenkinsResultsParserUtil.combine(
			"issues/", getNumber(), "/labels/", labelName);

		String url = JenkinsResultsParserUtil.getGitHubApiUrl(
			getRepositoryName(), getOwnerUsername(), path);

		try {
			JenkinsResultsParserUtil.toString(url, HttpRequestMethod.DELETE);

			refresh();
		}
		catch (IOException ioe) {
			System.out.println("Unable to remove label " + labelName);

			ioe.printStackTrace();
		}
	}

	public void setTestSuiteStatus(TestSuiteStatus testSuiteStatus) {
		setTestSuiteStatus(testSuiteStatus, null);
	}

	public void setTestSuiteStatus(
		TestSuiteStatus testSuiteStatus, String targetURL) {

		_testSuiteStatus = testSuiteStatus;

		StringBuilder sb = new StringBuilder();

		sb.append("ci:test");

		if (!_testSuiteName.equals(_TEST_SUITE_NAME_DEFAULT)) {
			sb.append(":");
			sb.append(_testSuiteName);
		}

		String testSuiteLabelPrefix = sb.toString();

		List<String> oldLabelNames = new ArrayList<>();

		for (Label label : getLabels()) {
			String name = label.getName();

			if (name.startsWith(testSuiteLabelPrefix)) {
				oldLabelNames.add(label.getName());
			}
		}

		for (String oldLabelName : oldLabelNames) {
			removeLabel(oldLabelName);
		}

		sb.append(" - ");
		sb.append(StringUtils.lowerCase(testSuiteStatus.toString()));

		GitHubRemoteRepository gitHubRemoteRepository = getRepository();

		Label testSuiteLabel = gitHubRemoteRepository.getLabel(sb.toString());

		if (testSuiteLabel == null) {
			if (gitHubRemoteRepository.addLabel(
					testSuiteStatus.getColor(), "", sb.toString())) {

				testSuiteLabel = gitHubRemoteRepository.getLabel(sb.toString());
			}
		}

		addLabel(testSuiteLabel);

		if (targetURL == null) {
			return;
		}

		if (testSuiteStatus == TestSuiteStatus.MISSING) {
			return;
		}

		Commit commit = getCommit();

		Commit.Status status = Commit.Status.valueOf(
			testSuiteStatus.toString());

		String context = _TEST_SUITE_NAME_DEFAULT;

		if (!_testSuiteName.equals(_TEST_SUITE_NAME_DEFAULT)) {
			context = "liferay/ci:test:" + _testSuiteName;
		}

		sb = new StringBuilder();

		sb.append("\"ci:test");

		if (!_testSuiteName.equals(_TEST_SUITE_NAME_DEFAULT)) {
			sb.append(":");
			sb.append(_testSuiteName);
		}

		sb.append("\"");

		if ((testSuiteStatus == TestSuiteStatus.ERROR) ||
			(testSuiteStatus == TestSuiteStatus.FAILURE)) {

			sb.append(" has FAILED.");
		}
		else if (testSuiteStatus == TestSuiteStatus.PENDING) {
			sb.append(" is running.");
		}
		else if (testSuiteStatus == TestSuiteStatus.SUCCESS) {
			sb.append(" has PASSED.");
		}

		commit.setStatus(status, context, sb.toString(), targetURL);
	}

	public static enum TestSuiteStatus {

		ERROR("fccdcc"), FAILURE("fccdcc"), MISSING("eeeeee"),
		PENDING("fff4c9"), SUCCESS("c7e8cb");

		public String getColor() {
			return _color;
		}

		private TestSuiteStatus(String color) {
			_color = color;
		}

		private final String _color;

	}

	protected String getIssueURL() {
		return _jsonObject.getString("issue_url");
	}

	protected String getURL() {
		return JenkinsResultsParserUtil.getGitHubApiUrl(
			_repositoryName, _ownerUsername, "pulls/" + _number);
	}

	protected void updateGithub() {
		JSONObject jsonObject = new JSONObject();

		List<String> labelNames = new ArrayList<>();

		for (Label label : _labels) {
			labelNames.add(label.getName());
		}

		jsonObject.put("labels", labelNames);

		try {
			JenkinsResultsParserUtil.toJSONObject(
				getIssueURL(), jsonObject.toString());
		}
		catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	private static final String _TEST_SUITE_NAME_DEFAULT = "default";

	private static final Pattern _htmlURLPattern = Pattern.compile(
		"https://github.com/(?<owner>[^/]+)/(?<repository>[^/]+)/pull/" +
			"(?<number>\\d+)");

	private JSONObject _jsonObject;
	private final List<Label> _labels = new ArrayList<>();
	private Integer _number;
	private String _ownerUsername;
	private GitHubRemoteRepository _repository;
	private String _repositoryName;
	private final String _testSuiteName;
	private TestSuiteStatus _testSuiteStatus = TestSuiteStatus.MISSING;

}