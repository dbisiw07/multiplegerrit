/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritAdministrativeMonitor;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil.PLUGIN_IMAGES_URL;

/**
 * Management link for configuring the global configuration of this trigger.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class GerritManagement extends ManagementLink implements StaplerProxy, Describable<GerritManagement>, Saveable {

    private static final Logger logger = LoggerFactory.getLogger(GerritManagement.class);

    private static final String NEW_SERVER = "New";

    @Override
    public String getIconFileName() {
        return PLUGIN_IMAGES_URL + "icon.png";
    }

    @Override
    public String getUrlName() {
        return "gerrit-trigger";
    }

    @Override
    public String getDisplayName() {
        return Messages.DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.PluginDescription();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<GerritManagement> {

        @Override
        public String getDisplayName() {
            return null; // unused
        }

        /**
         * Tests if the provided parameters can connect to Gerrit.
         * @param gerritHostName the hostname
         * @param gerritSshPort the ssh-port
         * @param gerritProxy the proxy url
         * @param gerritUserName the username
         * @param gerritAuthKeyFile the private key file
         * @param gerritAuthKeyFilePassword the password for the keyfile or null if there is none.
         * @return {@link FormValidation#ok() } if can be done,
         *         {@link FormValidation#error(java.lang.String) } otherwise.
         */
        public FormValidation doTestConnection(
                @QueryParameter("gerritHostName") final String gerritHostName,
                @QueryParameter("gerritSshPort") final int gerritSshPort,
                @QueryParameter("gerritProxy") final String gerritProxy,
                @QueryParameter("gerritUserName") final String gerritUserName,
                @QueryParameter("gerritAuthKeyFile") final String gerritAuthKeyFile,
                @QueryParameter("gerritAuthKeyFilePassword") final String gerritAuthKeyFilePassword) {
            if (logger.isDebugEnabled()) {
                logger.debug("gerritHostName = {}\n"
                        + "gerritSshPort = {}\n"
                        + "gerritProxy = {}\n"
                        + "gerritUserName = {}\n"
                        + "gerritAuthKeyFile = {}\n"
                        + "gerritAuthKeyFilePassword = {}",
                        new Object[]{gerritHostName,
                            gerritSshPort,
                            gerritProxy,
                            gerritUserName,
                            gerritAuthKeyFile,
                            gerritAuthKeyFilePassword,  });
            }

            File file = new File(gerritAuthKeyFile);
            String password = null;
            if (gerritAuthKeyFilePassword != null && gerritAuthKeyFilePassword.length() > 0) {
                password = gerritAuthKeyFilePassword;
            }
            if (SshUtil.checkPassPhrase(file, password)) {
                if (file.exists() && file.isFile()) {
                    try {
                        SshConnection sshConnection = SshConnectionFactory.getConnection(
                                gerritHostName,
                                gerritSshPort,
                                gerritProxy,
                                new Authentication(file, gerritUserName, password));
                        sshConnection.disconnect();
                        return FormValidation.ok(Messages.Success());

                    } catch (SshConnectException ex) {
                        return FormValidation.error(Messages.SshConnectException());
                    } catch (SshAuthenticationException ex) {
                        return FormValidation.error(Messages.SshAuthenticationException(ex.getMessage()));
                    } catch (Exception e) {
                        return FormValidation.error(Messages.ConnectionError(e.getMessage()));
                    }
                } else {
                    return FormValidation.error(Messages.SshKeyFileNotFoundError(gerritAuthKeyFile));
                }
            } else {
                return FormValidation.error(Messages.BadSshkeyOrPasswordError());
            }
    }
    }

    /**
    * Used when redirected to a server.
    * @param serverName the name of the server.
    * @return the GerritServer object.
    */
    public GerritServer getServer(String serverName) {
        if (NEW_SERVER.equalsIgnoreCase(serverName)) {
            return new GerritServer(NEW_SERVER);
        } else {
            return PluginImpl.getInstance().getServer(serverName);
        }
    }
    /**
     * Saves the form to the configuration and disk.
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws ServletException if something unfortunate happens.
     * @throws IOException if something unfortunate happens.
     * @throws InterruptedException if something unfortunate happens.
     */
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException,
            IOException,
            InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug("submit {}", req.toString());
        }
        JSONObject form = req.getSubmittedForm();

        PluginImpl plugin = PluginImpl.getInstance();

        ArrayList<GerritServer> servers = plugin.getServers();

        rsp.sendRedirect(".");
    }


    /**
     * Checks whether server name already exists.
     * @param value the value of the name field.
     * @return ok or error.
     */
    public FormValidation doNameFreeCheck(
            @QueryParameter("value")
            final String value) {
        if (serverListContainsName(value)) {
            return FormValidation.error("The server name " + value + " is already in use!");
        } else {
            return FormValidation.ok();
        }
    }

    @Override
    public Object getTarget() {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        return this;
    }

    @Override
    public void save() throws IOException {
        logger.debug("SAVE!!!");
    }

    /**
     * Returns this singleton.
     * @return the single loaded instance if this class.
     */
    public static GerritManagement get() {
        return ManagementLink.all().get(GerritManagement.class);
    }

    /**
     * Get the config of a server.
     *
     * @param serverName the name of the server for which we want to get the config.
     * @return the config.
     * @see GerritServer#getConfig()
     */
    public static IGerritHudsonTriggerConfig getConfig(String serverName) {
        return PluginImpl.getInstance().getServer(serverName).getConfig();
    }

    /**
     * The AdministrativeMonitor related to Gerrit.
     * convenience method for the jelly page.
     *
     * @return the monitor if it could be found, or null otherwise.
     */
    @SuppressWarnings("unused") //Called from Jelly
    public GerritAdministrativeMonitor getAdministrativeMonitor() {
        for (AdministrativeMonitor monitor : AdministrativeMonitor.all()) {
            if (monitor instanceof GerritAdministrativeMonitor) {
                return (GerritAdministrativeMonitor)monitor;
            }
        }
        return null;
    }

    /**
     * Get the list of Gerrit server names.
     *
     * @return the list of server names as an array.
     */
    public String[] getServerNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            names.add(s.getName());
        }
        return names.toArray(new String[names.size()]);
    }

    /**
     * Check whether the list of servers contains a GerritServer object of a specific name.
     *
     * @param name the name to check.
     * @return whether the list contains a server with the given name.
     */
    private boolean serverListContainsName(String name) {
        boolean contains = false;
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            if (s.getName().equals(name)) {
                contains = true;
            }
        }
        return contains;
    }

}
