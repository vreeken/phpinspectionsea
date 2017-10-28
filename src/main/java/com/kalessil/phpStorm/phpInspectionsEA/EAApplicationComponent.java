package com.kalessil.phpStorm.phpInspectionsEA;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.PluginId;
import com.kalessil.phpStorm.phpInspectionsEA.license.*;
import com.kalessil.phpStorm.phpInspectionsEA.utils.analytics.AnalyticsUtil;
import com.wyday.turboactivate.TurboActivate;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.jetbrains.annotations.NotNull;

public class EAApplicationComponent implements ApplicationComponent {
    private boolean updated;
    private boolean updateNotificationShown;

    private IdeaPluginDescriptor plugin;
    private LicenseService licenseService;

    @NotNull
    public static EAApplicationComponent getInstance() {
        return ApplicationManager.getApplication().getComponent(EAApplicationComponent.class);
    }

    /* TODO: separate component */
    private void initLicensing() {
        this.licenseService = new LicenseService();
        if (this.licenseService.shouldCheckPluginLicense()) {
            TurboActivate client = null;
            try {
                client = this.licenseService.getClient();
                if (!this.licenseService.isActiveLicense(client) && !this.licenseService.isActiveTrialLicense(client)) {
                    final String message;
                    if (client.IsActivated()) {
                        message = "The license has expired. Please <a href='#renew'>renew</a>.";
                    } else {
                        message = "A license need to be provided (<a href='#buy'>buy</a> one or <a href='#try'>start</a> a free trial).";
                    }
                    throw new RuntimeException(message);
                }
            } catch (Throwable failure) {
                final LicenseService service  = this.licenseService;
                final TurboActivate adapter   = client;

                final String message          = failure.getMessage();
                final String pluginName       = this.plugin.getName();
                final NotificationGroup group = new NotificationGroup(pluginName, NotificationDisplayType.STICKY_BALLOON, true);
                Notifications.Bus.notify(group.createNotification(
                    "<b>" + pluginName + "</b>",
                    message == null ? failure.getClass().getName() : message,
                    NotificationType.WARNING,
                    EaNotificationLinksHandler.TAKE_LICENSE_ACTION_LISTENER.withActionCallback(action -> {
                        switch (action) {
                            case "#try":   (new StartTrialAction()).perform(service, adapter, plugin); break;
                            case "#buy":   (new PurchaseLicenseAction()).perform(adapter, plugin);     break;
                            case "#renew": (new RenewLicenseAction()).perform(adapter, plugin);        break;
                        }
                    })
                ));
            }
        }
    }

    @Override
    public void initComponent() {
        this.plugin = PluginManager.getPlugin(PluginId.getId("com.kalessil.phpStorm.phpInspectionsEA"));
        if (null == plugin) {
            return;
        }

        final EASettings settings = EASettings.getInstance();

        /* collect installation events (anonymous) */
        this.updated = !this.plugin.getVersion().equals(settings.getVersion());
        if (this.updated) {
            settings.setVersion(this.plugin.getVersion());
            AnalyticsUtil.registerPluginEvent(settings, "install", settings.getOldestVersion());
        }
        AnalyticsUtil.registerPluginEvent(settings, "run", settings.getOldestVersion());

        /* collect exceptions */
        final FileAppender appender = new FileAppender() {
            @Override
            public void append(@NotNull LoggingEvent event) {
                final ThrowableInformation exceptionDetails = event.getThrowableInformation();
                if (exceptionDetails != null) {
                    AnalyticsUtil.registerLoggedException(
                        settings.getVersion(),
                        settings.getUuid(),
                        exceptionDetails.getThrowable()
                    );
                }
            }
        };
        appender.setName("ea-exceptions-tracker");
        Logger.getRootLogger().addAppender(appender);

        this.initLicensing();
    }

    @Override
    public void disposeComponent() {
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "EAApplicationComponent";
    }

    boolean isUpdated() {
        return this.updated;
    }

    boolean isUpdateNotificationShown() {
        return this.updateNotificationShown;
    }

    void setUpdateNotificationShown() {
        this.updateNotificationShown = true;
    }
}
