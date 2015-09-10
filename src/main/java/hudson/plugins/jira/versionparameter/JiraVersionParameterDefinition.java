package hudson.plugins.jira.versionparameter;

import com.atlassian.jira.rest.client.api.domain.Version;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.plugins.jira.JiraSession;
import hudson.plugins.jira.JiraSite;
import net.sf.json.JSONObject;

import org.joda.time.DateTime;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static hudson.Util.fixNull;

public class JiraVersionParameterDefinition extends ParameterDefinition {
    private static final long serialVersionUID = 4232979892748310160L;

    private String projectKey;
    private boolean showReleased = false;
    private boolean showArchived = false;
    private boolean showFuture = false;
    private boolean showResolved = false;
    private Pattern pattern = null;

    @DataBoundConstructor
    public JiraVersionParameterDefinition(String name, String description, String jiraProjectKey, String jiraReleasePattern, String jiraShowReleased,
                                          String jiraShowArchived, String jiraShowFuture, String jiraShowResolved) {
        super(name, description);
        setJiraProjectKey(jiraProjectKey);
        setJiraReleasePattern(jiraReleasePattern);
        setJiraShowReleased(jiraShowReleased);
        setJiraShowArchived(jiraShowArchived);
        setJiraShowFuture(jiraShowFuture);
        setJiraShowResolved(jiraShowResolved);
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] values = req.getParameterValues(getName());
        if (values == null || values.length != 1) {
            return null;
        }
        return new JiraVersionParameterValue(getName(), values[0]);
    }


    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
        JiraVersionParameterValue value = req.bindJSON(JiraVersionParameterValue.class, formData);
        return value;
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        return new JiraVersionParameterValue(getName(), value);
    }

    public List<JiraVersionParameterDefinition.Result> getVersions() throws IOException {
        AbstractProject<?, ?> context = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);

        JiraSite site = JiraSite.get(context);
        if (site == null)
            throw new IllegalStateException("JIRA site needs to be configured in the project " + context.getFullDisplayName());

        JiraSession session = site.createSession();
        if (session == null) throw new IllegalStateException("Remote access for JIRA isn't configured in Jenkins");

        List<Version> versions = session.getVersions(projectKey);

        List<Result> projectVersions = new ArrayList<Result>();

        for (Version version : fixNull(versions)) {
            if (match(version)) projectVersions.add(new Result(version));
        }

        return projectVersions;
    }

    private boolean match(Version version) throws IOException {
        // Match regex if it exists
        if (pattern != null) {
            if (!pattern.matcher(version.getName()).matches()) return false;
        }

        // Filter released versions
        if (!showReleased && version.isReleased()) return false;

        // Filter archived versions
        if (!showArchived && version.isArchived()) return false;

        // Filter future versions before today at midnight
        if (showFuture && version.getReleaseDate().isBefore(new DateTime().toDateMidnight())) return false;

        // Filter all issues resolved versions
        if (showResolved) {
            AbstractProject<?, ?> context = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);

            JiraSite site = JiraSite.get(context);
            JiraSession session = site.createSession();
            if(version.getId() == null) return false;
            return !session.hasVersionUnresolvedIssues(version.getId());
        }

        return true;
    }

    public String getJiraReleasePattern() {
        if (pattern == null) return "";
        return pattern.pattern();
    }

    public void setJiraReleasePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            this.pattern = null;
        } else {
            this.pattern = Pattern.compile(pattern);
        }
    }

    public String getJiraProjectKey() {
        return projectKey;
    }

    public void setJiraProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getJiraShowReleased() {
        return Boolean.toString(showReleased);
    }

    public void setJiraShowReleased(String showReleased) {
        this.showReleased = Boolean.parseBoolean(showReleased);
    }


    public String getJiraShowArchived() {
        return Boolean.toString(showArchived);
    }

    public void setJiraShowArchived(String showArchived) {
        this.showArchived = Boolean.parseBoolean(showArchived);
    }

    public String getJiraShowFuture() {
        return Boolean.toString(showFuture);
    }

    public void setJiraShowFuture(String showFuture) {
        this.showFuture = Boolean.parseBoolean(showFuture);
    }

    public String getJiraShowResolved() {
        return Boolean.toString(showResolved);
    }

    public void setJiraShowResolved(String showResolved) {
        this.showResolved = Boolean.parseBoolean(showResolved);
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "JIRA Release Version Parameter";
        }
    }

    public static class Result {
        public final String name;
        public final String displayName;
        public final Long id;
        private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

        public Result(final Version version) {
            this.name = version.getName();
            this.id = version.getId();
            this.displayName = version.getName() + " (Release date: " + simpleDateFormat.format(version.getReleaseDate().toDate()) + ")";
        }
    }
}
