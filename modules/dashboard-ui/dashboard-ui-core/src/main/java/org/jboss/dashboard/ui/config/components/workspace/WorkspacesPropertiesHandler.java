/**
 * Copyright (C) 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.dashboard.ui.config.components.workspace;

import org.jboss.dashboard.LocaleManager;
import org.jboss.dashboard.SecurityServices;
import org.jboss.dashboard.database.hibernate.HibernateTxFragment;
import org.jboss.dashboard.security.UIPolicy;
import org.jboss.dashboard.security.principals.RolePrincipal;
import org.jboss.dashboard.ui.UIServices;
import org.jboss.dashboard.ui.components.BeanHandler;
import org.jboss.dashboard.ui.formatters.FactoryURL;
import org.jboss.dashboard.ui.components.MessagesComponentHandler;
import org.jboss.dashboard.ui.controller.CommandRequest;
import org.jboss.dashboard.ui.NavigationManager;
import org.jboss.dashboard.ui.components.WorkspaceHandler;
import org.jboss.dashboard.users.Role;
import org.jboss.dashboard.users.RolesManager;
import org.jboss.dashboard.workspace.WorkspaceImpl;
import org.jboss.dashboard.users.UserStatus;
import org.hibernate.Session;
import org.slf4j.Logger;

import java.security.Permission;
import java.util.*;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;

@SessionScoped
public class WorkspacesPropertiesHandler extends BeanHandler {

    @Inject
    protected transient Logger log;

    @Inject
    private NavigationManager navigationManager;

    @Inject
    private WorkspaceHandler workspaceHandler;

    private String workspaceId;
    private String skinId;
    private String envelopeId;
    private Map<String, String> name;
    private Map<String, String> title;

    public WorkspaceHandler getWorkspaceHandler() {
        return workspaceHandler;
    }

    public void setWorkspaceHandler(WorkspaceHandler workspaceHandler) {
        this.workspaceHandler = workspaceHandler;
    }

    public NavigationManager getNavigationManager() {
        return navigationManager;
    }

    public void setNavigationManager(NavigationManager navigationManager) {
        this.navigationManager = navigationManager;
    }

    public String getSkinId() {
        return skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = skinId;
    }

    public String getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(String envelopeId) {
        this.envelopeId = envelopeId;
    }

    public Map<String, String> getName() {
        return name;
    }

    public void setName(Map<String, String> name) {
        this.name = name;
    }

    public Map<String, String> getTitle() {
        return title;
    }

    public void setTitle(Map<String, String> title) {
        this.title = title;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public void actionDeleteWorkspace(CommandRequest request) throws Exception {
        String workspaceId = request.getParameter("workspaceId");
        if (this.getFieldErrors() != null && this.getFieldErrors().size() > 0) this.clearFieldErrors();
        getWorkspaceHandler().setWorkspaceId(workspaceId);
        getWorkspaceHandler().deleteWorkspace();
    }

    public void actionCreateWorkspace(CommandRequest request) {
        MessagesComponentHandler messagesHandler = MessagesComponentHandler.lookup();
        final WorkspaceImpl newWorkspace = new WorkspaceImpl();
        try {
            buildI18nValues(request);
            if (validateBeforeCreation()) {
                newWorkspace.setId(UIServices.lookup().getWorkspacesManager().generateWorkspaceId());
                newWorkspace.setTitle(title);
                newWorkspace.setName(name);
                newWorkspace.setSkinId(skinId);
                newWorkspace.setEnvelopeId(envelopeId);

                // Register workspace (persistent operation)
                new HibernateTxFragment() {
                protected void txFragment(Session session) throws Exception {
                    UIServices.lookup().getWorkspacesManager().addNewWorkspace(newWorkspace);
                    getNavigationManager().setCurrentWorkspace(newWorkspace);
                }}.execute();

                // Add default access/admin permissions to the roles the creator user belongs to.
                RolesManager rolesManager = SecurityServices.lookup().getRolesManager();
                UIPolicy policy = (UIPolicy) SecurityServices.lookup().getSecurityPolicy();
                List<Permission> defaultPermissions = policy.createDefaultPermissions(newWorkspace);
                for (String roleId : UserStatus.lookup().getUserRoleIds()) {
                    Role role = rolesManager.getRoleById(roleId);
                    RolePrincipal prpal = new RolePrincipal(role);
                    for (Permission perm : defaultPermissions) {
                        policy.addPermission(prpal, perm);
                    }
                }
                policy.save();

                title = null;
                name = null;
                skinId = UIServices.lookup().getSkinsManager().getDefaultElement().getId();
                envelopeId = UIServices.lookup().getEnvelopesManager().getDefaultElement().getId();
                messagesHandler.addMessage("ui.alert.workspaceCreation.OK");
            }

        } catch (Exception e) {
            messagesHandler.clearAll();
            messagesHandler.addError("ui.alert.workspaceCreation.KO");
            log.error("Error: " + e.getMessage());
        }
    }

    protected boolean validateBeforeCreation() {
        MessagesComponentHandler messagesHandler = MessagesComponentHandler.lookup();
        messagesHandler.clearAll();
        boolean valid = validate();
        if (!valid) messagesHandler.getErrorsToDisplay().add(0, "ui.alert.workspaceCreation.KO");
        return valid;
    }

    public boolean validate() {
        MessagesComponentHandler messagesHandler = MessagesComponentHandler.lookup();
        boolean valid = true;
        if (name == null || name.isEmpty()) {
            addFieldError(new FactoryURL(getBeanName(), "name"), null, name);
            messagesHandler.addError("ui.alert.workspaceErrors.name");
            valid = false;
        }
        if (title == null || title.isEmpty()) {
            addFieldError(new FactoryURL(getBeanName(), "title"), null, title);
            messagesHandler.addError("ui.alert.workspaceErrors.title");
            valid = false;
        }
        return valid;
    }


    public void actionDiagnoseWorkspaces(CommandRequest request) throws Exception {
        Set<String> workspaceIds = UIServices.lookup().getWorkspacesManager().getAllWorkspacesIdentifiers();
        for (String wsId : workspaceIds) {
            WorkspaceImpl workspace = (WorkspaceImpl) UIServices.lookup().getWorkspacesManager().getWorkspace(wsId);
            int numErrors = workspace.sectionsDiagnose();
            log.error("Found " + numErrors + " page Errors.");
        }
    }

    public void actionDiagnoseWorkspacesAndFix(CommandRequest request) throws Exception {
        Set<String> workspaceIds = UIServices.lookup().getWorkspacesManager().getAllWorkspacesIdentifiers();
        for (String wsId : workspaceIds) {
            WorkspaceImpl workspace = (WorkspaceImpl) UIServices.lookup().getWorkspacesManager().getWorkspace(wsId);
            workspace.sectionsDiagnoseFix();
        }
    }

    protected void buildI18nValues(CommandRequest request) {
        name = buildI18n(request, "name");
        title = buildI18n(request, "title");
    }

    protected Map<String, String> buildI18n(CommandRequest request, String fieldName) {
        Map<String, String> result = new HashMap<String, String>();
        String[] langs = LocaleManager.lookup().getPlatformAvailableLangs();
        if (langs != null) {
            for (String lang : langs) {
                String name = fieldName + "_" + lang;
                String value = request.getParameter(name);
                if (value != null && !"".equals(value)) {
                    result.put(lang, value);
                }
            }
        }
        return result;
    }
}
