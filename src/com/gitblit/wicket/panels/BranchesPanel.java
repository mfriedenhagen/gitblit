/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.panels;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.SyndicationServlet;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.GitSearchPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.MetricsPage;
import com.gitblit.wicket.pages.TreePage;

public class BranchesPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasBranches;

	public BranchesPanel(String wicketId, final RepositoryModel model, Repository r,
			final int maxCount, final boolean showAdmin) {
		super(wicketId);

		// branches
		List<RefModel> branches = new ArrayList<RefModel>();
		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}

		List<RefModel> localBranches = JGitUtils.getLocalBranches(r, false, -1);
		for (RefModel refModel : localBranches) {
			if (user.hasBranchPermission(model.name, refModel.reference.getName())) {
				branches.add(refModel);
			}
		}
		if (model.showRemoteBranches) {
			List<RefModel> remoteBranches = JGitUtils.getRemoteBranches(r, false, -1);
			for (RefModel refModel : remoteBranches) {
				if (user.hasBranchPermission(model.name, refModel.reference.getName())) {
					branches.add(refModel);
				}
			}
		}
		Collections.sort(branches);
		Collections.reverse(branches);
		if (maxCount > 0 && branches.size() > maxCount) {
			branches = new ArrayList<RefModel>(branches.subList(0, maxCount));
		}

		if (maxCount > 0) {
			// summary page
			// show branches page link
			add(new LinkPanel("branches", "title", new StringResourceModel("gb.branches", this,
					null), BranchesPage.class, WicketUtils.newRepositoryParameter(model.name)));
		} else {
			// branches page
			add(new Label("branches", new StringResourceModel("gb.branches", this, null)));
		}
		
		// only allow delete if we have multiple branches
		final boolean showDelete = showAdmin && branches.size() > 1;
		
		ListDataProvider<RefModel> branchesDp = new ListDataProvider<RefModel>(branches);
		DataView<RefModel> branchesView = new DataView<RefModel>("branch", branchesDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			public void populateItem(final Item<RefModel> item) {
				final RefModel entry = item.getModelObject();

				item.add(WicketUtils.createDateLabel("branchDate", entry.getDate(), getTimeZone(), getTimeUtils()));

				item.add(new LinkPanel("branchName", "list name", StringUtils.trimString(
						entry.displayName, 28), LogPage.class, WicketUtils.newObjectParameter(
						model.name, entry.getName())));

				String author = entry.getAuthorIdent().getName();
				LinkPanel authorLink = new LinkPanel("branchAuthor", "list", author,
						GitSearchPage.class, WicketUtils.newSearchParameter(model.name,
								entry.getName(), author, Constants.SearchType.AUTHOR));
				setPersonSearchTooltip(authorLink, author, Constants.SearchType.AUTHOR);
				item.add(authorLink);

				// short message
				String shortMessage = entry.getShortMessage();
				String trimmedMessage = StringUtils.trimString(shortMessage, Constants.LEN_SHORTLOG);
				LinkPanel shortlog = new LinkPanel("branchLog", "list subject", trimmedMessage,
						CommitPage.class, WicketUtils.newObjectParameter(model.name,
								entry.getName()));
				if (!shortMessage.equals(trimmedMessage)) {
					WicketUtils.setHtmlTooltip(shortlog, shortMessage);
				}
				item.add(shortlog);
				
				if (maxCount <= 0) {
					Fragment fragment = new Fragment("branchLinks", showDelete? "branchPageAdminLinks" : "branchPageLinks", this);
					fragment.add(new BookmarkablePageLink<Void>("log", LogPage.class, WicketUtils
							.newObjectParameter(model.name, entry.getName())));
					fragment.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils
							.newObjectParameter(model.name, entry.getName())));
					fragment.add(new BookmarkablePageLink<Void>("metrics", MetricsPage.class,
							WicketUtils.newObjectParameter(model.name, entry.getName())));
					fragment.add(new ExternalLink("syndication", SyndicationServlet.asLink(
							getRequest().getRelativePathPrefixToContextRoot(), model.name,
							entry.getName(), 0)));
					if (showDelete) {
						fragment.add(createDeleteBranchLink(model, entry));
					}
					item.add(fragment);
				} else {
					Fragment fragment = new Fragment("branchLinks", "branchPanelLinks", this);
					fragment.add(new BookmarkablePageLink<Void>("log", LogPage.class, WicketUtils
							.newObjectParameter(model.name, entry.getName())));
					fragment.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils
							.newObjectParameter(model.name, entry.getName())));
					item.add(fragment);
				}
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(branchesView);
		if (branches.size() < maxCount || maxCount <= 0) {
			add(new Label("allBranches", "").setVisible(false));
		} else {
			add(new LinkPanel("allBranches", "link", new StringResourceModel("gb.allBranches",
					this, null), BranchesPage.class, WicketUtils.newRepositoryParameter(model.name)));
		}
		// We always have 1 branch
		hasBranches = (branches.size() > 1)
				|| ((branches.size() == 1) && !branches.get(0).displayName
						.equalsIgnoreCase("master"));
	}

	public BranchesPanel hideIfEmpty() {
		setVisible(hasBranches);
		return this;
	}

	private Link<Void> createDeleteBranchLink(final RepositoryModel repositoryModel, final RefModel entry)
	{
		Link<Void> deleteLink = new Link<Void>("deleteBranch") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick() {
				Repository r = GitBlit.self().getRepository(repositoryModel.name);
				if (r == null) {
					if (GitBlit.self().isCollectingGarbage(repositoryModel.name)) {
						error(MessageFormat.format(getString("gb.busyCollectingGarbage"), repositoryModel.name));
					} else {
						error(MessageFormat.format("Failed to find repository {0}", repositoryModel.name));
					}
					return;
				}
				boolean success = JGitUtils.deleteBranchRef(r, entry.getName());
				r.close();
				if (success) {
					info(MessageFormat.format("Branch \"{0}\" deleted", entry.displayName));
					// redirect to the owning page
					setResponsePage(getPage().getClass(), WicketUtils.newRepositoryParameter(repositoryModel.name));
				}
				else {
					error(MessageFormat.format("Failed to delete branch \"{0}\"", entry.displayName));
				}
			}
		};
		
		deleteLink.add(new JavascriptEventConfirmation("onclick", MessageFormat.format(
				"Delete branch \"{0}\"?", entry.displayName )));
		return deleteLink;
	}
}
