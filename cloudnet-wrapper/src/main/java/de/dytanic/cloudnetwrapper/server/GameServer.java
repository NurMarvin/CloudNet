/*
 * Copyright (c) Tarek Hosni El Alaoui 2017
 */

package de.dytanic.cloudnetwrapper.server;

import de.dytanic.cloudnet.lib.ConnectableAddress;
import de.dytanic.cloudnet.lib.NetworkUtils;
import de.dytanic.cloudnet.lib.server.ServerGroup;
import de.dytanic.cloudnet.lib.server.ServerGroupMode;
import de.dytanic.cloudnet.lib.server.ServerGroupType;
import de.dytanic.cloudnet.lib.server.ServerState;
import de.dytanic.cloudnet.lib.server.info.ServerInfo;
import de.dytanic.cloudnet.lib.server.template.MasterTemplateLoader;
import de.dytanic.cloudnet.lib.server.template.Template;
import de.dytanic.cloudnet.lib.server.template.TemplateLoader;
import de.dytanic.cloudnet.lib.server.template.TemplateResource;
import de.dytanic.cloudnet.lib.service.ServiceId;
import de.dytanic.cloudnet.lib.service.plugin.ServerInstallablePlugin;
import de.dytanic.cloudnet.lib.user.SimpledUser;
import de.dytanic.cloudnet.lib.utility.Acceptable;
import de.dytanic.cloudnet.lib.utility.CollectionWrapper;
import de.dytanic.cloudnet.lib.utility.document.Document;
import de.dytanic.cloudnetwrapper.CloudNetWrapper;
import de.dytanic.cloudnetwrapper.network.packet.out.PacketOutAddServer;
import de.dytanic.cloudnetwrapper.network.packet.out.PacketOutRemoveServer;
import de.dytanic.cloudnetwrapper.server.process.ServerDispatcher;
import de.dytanic.cloudnetwrapper.server.process.ServerProcess;
import de.dytanic.cloudnetwrapper.util.FileUtility;
import de.dytanic.cloudnetwrapper.util.MasterTemplateDeploy;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.*;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

@EqualsAndHashCode
@Getter
public class GameServer implements ServerDispatcher {

    private ServerProcess serverProcess;

    private ServerStage serverStage;

    private ServerGroup serverGroup;

    private Process instance;

    private ServerInfo serverInfo;

    private long startupTimeStamp = 0;

    private Path dir;

    private String path, custom;

    public GameServer(ServerProcess serverProcess, ServerStage serverStage, ServerGroup serverGroup)
    {
        this.serverProcess = serverProcess;
        this.serverStage = serverStage;
        this.serverGroup = serverGroup;

        if (this.serverProcess.getMeta().getServerConfig().getProperties().contains(NetworkUtils.DEV_PROPERTY))
        {
            this.path = CloudNetWrapper.getInstance().getWrapperConfig().getDevServicePath() + NetworkUtils.SLASH_STRING + serverProcess.getMeta().getServiceId().getServerId();
            this.custom = this.serverProcess.getMeta().getCustomServerDownload();
        } else
        {
            if (serverGroup.getGroupMode().equals(ServerGroupMode.STATIC) || serverGroup.getGroupMode().equals(ServerGroupMode.STATIC_LOBBY))
                this.path = "local/servers/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + this.serverProcess.getMeta().getServiceId().getServerId();
            else
                this.path = "temp/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + serverProcess.getMeta().getServiceId();
        }

        this.dir = Paths.get(path);
    }

    @Override
    public boolean bootstrap() throws Exception
    {
        serverProcess.setServerStage(ServerStage.DOWNLOAD);
        long startupTime = System.currentTimeMillis();

        for (ServerInstallablePlugin url : serverProcess.getMeta().getDownloadablePlugins())
        {
            switch (url.getPluginResourceType())
            {
                case URL:
                {
                    if (!Files.exists(Paths.get("local/cache/web_plugins/" + url.getName() + ".jar")))
                    {
                        try
                        {
                            URLConnection urlConnection = new java.net.URL(url.getUrl()).openConnection();
                            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
                            Files.copy(urlConnection.getInputStream(), Paths.get("local/cache/web_plugins/" + url.getName() + ".jar"));
                        } catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }
                break;
                case MASTER:
                {
                    if (!Files.exists(Paths.get("local/cache/web_plugins/" + url.getName() + ".jar")) && CloudNetWrapper.getInstance().getSimpledUser() != null)
                    {
                        try
                        {
                            URLConnection urlConnection = new java.net.URL(new StringBuilder(CloudNetWrapper.getInstance().getOptionSet().has("ssl") ? "https://" : "http://")
                                    .append(CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost())
                                    .append(":")
                                    .append(CloudNetWrapper.getInstance().getWrapperConfig().getWebPort())
                                    .append("/cloudnet/api/v1/download").substring(0)).openConnection();
                            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

                            SimpledUser simpledUser = CloudNetWrapper.getInstance().getSimpledUser();
                            urlConnection.setRequestProperty("-Xcloudnet-user", simpledUser.getUserName());
                            urlConnection.setRequestProperty("-Xcloudnet-token", simpledUser.getApiToken());
                            urlConnection.setRequestProperty("-Xmessage", "plugin");
                            urlConnection.setRequestProperty("-Xvalue", url.getName());

                            urlConnection.connect();
                            System.out.println("Downloading " + url.getName() + ".jar");
                            Files.copy(urlConnection.getInputStream(), Paths.get("local/cache/web_plugins/" + url.getName() + ".jar"));
                            System.out.println("Download was completed successfully!");
                        } catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }
                break;
                default:
                    break;
            }
        }

        for (ServerInstallablePlugin url : serverProcess.getMeta().getTemplate().getInstallablePlugins())
        {
            switch (url.getPluginResourceType())
            {
                case URL:
                {
                    if (!Files.exists(Paths.get("local/cache/web_plugins/" + url.getName() + ".jar")))
                    {
                        try
                        {
                            URLConnection urlConnection = new java.net.URL(url.getUrl()).openConnection();
                            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
                            Files.copy(urlConnection.getInputStream(), Paths.get("local/cache/web_plugins/" + url.getName() + ".jar"));
                        } catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }
                break;
                case MASTER:
                {
                    if (!Files.exists(Paths.get("local/cache/web_plugins/" + url.getName() + ".jar")) && CloudNetWrapper.getInstance().getSimpledUser() != null)
                    {
                        try
                        {
                            URLConnection urlConnection = new java.net.URL(new StringBuilder(CloudNetWrapper.getInstance().getOptionSet().has("ssl") ? "https://" : "http://")
                                    .append(CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost())
                                    .append(":")
                                    .append(CloudNetWrapper.getInstance().getWrapperConfig().getWebPort())
                                    .append("/cloudnet/api/v1/download").substring(0)).openConnection();
                            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

                            SimpledUser simpledUser = CloudNetWrapper.getInstance().getSimpledUser();
                            urlConnection.setRequestProperty("-Xcloudnet-user", simpledUser.getUserName());
                            urlConnection.setRequestProperty("-Xcloudnet-token", simpledUser.getApiToken());
                            urlConnection.setRequestProperty("-Xmessage", "plugin");
                            urlConnection.setRequestProperty("-Xvalue", url.getName());

                            urlConnection.connect();
                            System.out.println("Downloading " + url.getName() + ".jar");
                            Files.copy(urlConnection.getInputStream(), Paths.get("local/cache/web_plugins/" + url.getName() + ".jar"));
                            System.out.println("Download was completed successfully!");
                        } catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }
                break;
                default:
                    break;
            }
        }

        if (serverGroup.getTemplates().size() == 0 && serverProcess.getMeta().getUrl() == null) return false;

        if (serverGroup.getGroupMode().equals(ServerGroupMode.STATIC))
        {
            if (!Files.exists(dir)) if (!a()) return false;
        } else if (!a()) return false;

        for (ServerInstallablePlugin plugin : serverProcess.getMeta().getDownloadablePlugins())
            FileUtility.copyFileToDirectory(new File("local/cache/web_plugins/" + plugin.getName() + ".jar"), new File(path + "/plugins"));

        for (ServerInstallablePlugin plugin : serverProcess.getMeta().getTemplate().getInstallablePlugins())
            FileUtility.copyFileToDirectory(new File("local/cache/web_plugins/" + plugin.getName() + ".jar"), new File(path + "/plugins"));

        serverProcess.setServerStage(ServerStage.COPY);

        if (serverGroup.getServerType().equals(ServerGroupType.BUKKIT))
            if (!Files.exists(Paths.get(path + "/spigot.jar")))
                FileUtility.copyFileToDirectory(new File("local/spigot.jar"), new File(path));

        if (serverGroup.getServerType().equals(ServerGroupType.GLOWSTONE))
        {
            if (!Files.exists(Paths.get(path + "/config")))
                Files.createDirectories(Paths.get(path + "/config"));
            if (!Files.exists(Paths.get(path + "/config/glowstone.yml")))
                FileUtility.insertData("files/glowstone.yml", path + "/config/glowstone.yml");
        }

        if (!Files.exists(Paths.get(path + "/server.properties")))
            FileUtility.insertData("files/server.properties", path + "/server.properties");

        if (!Files.exists(Paths.get(path + "/bukkit.yml")))
            FileUtility.insertData("files/bukkit.yml", path + "/bukkit.yml");

        if (!serverProcess.getMeta().isOnlineMode())
            if (!Files.exists(Paths.get(path + "/spigot.yml")))
                FileUtility.insertData("files/spigot.yml", path + "/spigot.yml");

        Files.deleteIfExists(Paths.get(path + "/plugins/CloudNetAPI.jar"));
        FileUtility.insertData("files/CloudNetAPI.jar", path + "/plugins/CloudNetAPI.jar");

        FileUtility.copyFilesInDirectory(new File("local/global"), new File(path));

        String motd = "Default Motd";
        int maxPlayers = 0;

        if (!serverGroup.getServerType().equals(ServerGroupType.GLOWSTONE))
        {
            Properties properties = new Properties();
            try (InputStreamReader inputStreamReader = new InputStreamReader(Files.newInputStream(Paths.get(path + "/server.properties"))))
            {
                properties.load(inputStreamReader);
            }

            Enumeration enumeration = this.serverProcess.getMeta().getServerProperties().keys();
            while (enumeration.hasMoreElements())
            {
                String x = enumeration.nextElement().toString();
                properties.setProperty(x, this.serverProcess.getMeta().getServerProperties().getProperty(x));
            }

            properties.setProperty("server-ip", CloudNetWrapper.getInstance().getWrapperConfig().getInternalIP());
            properties.setProperty("server-port", serverProcess.getMeta().getPort() + NetworkUtils.EMPTY_STRING);
            properties.setProperty("online-mode", serverProcess.getMeta().isOnlineMode() + NetworkUtils.EMPTY_STRING);
            //properties.setProperty("server-name", serverProcess.getMeta().getServiceId().getServerId());

            motd = properties.getProperty("motd");
            maxPlayers = Integer.parseInt(properties.getProperty("max-players"));

            try (OutputStream outputStream = Files.newOutputStream(Paths.get(path + "/server.properties")))
            {
                properties.store(outputStream, "CloudNet-Wrapper EDIT");
            }
        } else
        {
            try (InputStreamReader inputStreamReader = new InputStreamReader(Files.newInputStream(Paths.get(path + "/config/glowstone.yml")), StandardCharsets.UTF_8))
            {
                Configuration configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(inputStreamReader);
                Configuration section = configuration.getSection("server");
                section.set("ip", CloudNetWrapper.getInstance().getWrapperConfig().getInternalIP());
                section.set("port", serverProcess.getMeta().getPort());

                maxPlayers = section.getInt("max-players");
                motd = section.getString("motd");

                configuration.set("server", section);
                configuration.set("console.use-jline", false);
                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(Paths.get(path + "/config/glowstone.yml")), StandardCharsets.UTF_8))
                {
                    ConfigurationProvider.getProvider(YamlConfiguration.class).save(configuration, outputStreamWriter);
                }
            }
        }
        this.serverInfo = new ServerInfo(serverProcess.getMeta().getServiceId(),
                CloudNetWrapper.getInstance().getWrapperConfig().getInternalIP(),
                this.getServerProcess().getMeta().getPort(), false, new ArrayList<>(), serverProcess.getMeta().getMemory(), motd, 0,
                maxPlayers, ServerState.OFFLINE, this.serverProcess.getMeta().getServerConfig(), serverProcess.getMeta().getTemplate());

        if (!Files.exists(Paths.get(path + "/CLOUD")))
        {
            Files.createDirectory(Paths.get(path + "/CLOUD"));
        }

        new Document()
                .append("serviceId", serverProcess.getMeta().getServiceId())
                .append("serverProcess", serverProcess.getMeta())
                .append("serverInfo", serverInfo)
                .append("ssl", CloudNetWrapper.getInstance().getOptionSet().has("ssl"))
                .append("memory", serverProcess.getMeta().getMemory()).saveAsConfig(Paths.get(path + "/CLOUD/config.json"));

        new Document()
                .append("connection", new ConnectableAddress(
                        CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost(),
                        CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetPort())).saveAsConfig(Paths.get(path + "/CLOUD/connection.json"));

        CloudNetWrapper.getInstance().getNetworkConnection().sendPacket(new PacketOutAddServer(this.serverInfo, this.serverProcess.getMeta()));
        System.out.println("Server " + toString() + " started in [" + (System.currentTimeMillis() - startupTime) + " milliseconds]");
        this.startupTimeStamp = System.currentTimeMillis();

        startProcess();

        serverProcess.setServerStage(ServerStage.PROCESS);
        CloudNetWrapper.getInstance().getServers().put(this.serverProcess.getMeta().getServiceId().getServerId(), this);
        serverProcess.setServerStage(ServerStage.NET_INIT);
        return true;
    }

    public void restart()
    {

        kill();
        System.out.println("Server " + toString() + " was killed and restart...");
        try
        {
            startProcess();
            startupTimeStamp = System.currentTimeMillis();
            System.out.println("Server " + toString() + " restarted now!");
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public boolean shutdown()
    {

        if (instance == null)
        {
            if (serverGroup.getGroupMode().equals(ServerGroupMode.DYNAMIC))
            {
                FileUtility.deleteDirectory(dir.toFile());
            }
            return true;
        }

        kill();

        if (CloudNetWrapper.getInstance().getWrapperConfig().isSavingRecords())
        {
            try
            {
                File directory = new File("local/records/" + serverProcess.getMeta().getServiceId().toString());

                FileUtility.copyFilesInDirectory(new File(path + "/logs"), directory);
                FileUtility.copyFilesInDirectory(new File(path + "/crash-reports"), directory);

                new Document("meta", serverProcess.getMeta()).saveAsConfig(Paths.get("local/records/" + serverProcess.getMeta().getServiceId().toString() + "/metadata.json"));
            } catch (IOException ignored)
            {
            }
        }

        if (serverGroup.isMaintenance() && (CloudNetWrapper.getInstance().getWrapperConfig().isMaintenance_copy() && !serverGroup.getGroupMode().equals(ServerGroupMode.STATIC)))
            copy();

        if (!serverGroup.getGroupMode().equals(ServerGroupMode.STATIC) && !serverGroup.getGroupMode().equals(ServerGroupMode.STATIC_LOBBY))
            try
            {
                FileUtility.deleteDirectory(dir.toFile());
            } catch (Exception ex)
            {
                ex.printStackTrace();
            }

        CloudNetWrapper.getInstance().getServers().remove(getServiceId().getServerId());
        CloudNetWrapper.getInstance().getNetworkConnection().sendPacket(new PacketOutRemoveServer(serverInfo));
        System.out.println("Server " + toString() + " was stopped");
        return true;
    }

    public void copy()
    {
        copy(null);
    }

    public void copy(Template template)
    {

        if (template == null) template = this.serverProcess.getMeta().getTemplate();

        if (instance != null && instance.isAlive())
        {
            executeCommand("save-all");
            try
            {
                Thread.sleep(1000);
            } catch (InterruptedException e)
            {
            }
        }

        Template x = CollectionWrapper.filter(serverGroup.getTemplates(), new Acceptable<Template>() {
            @Override
            public boolean isAccepted(Template template)
            {
                return template != null && serverProcess.getMeta().getTemplate().getName().equals(template.getName());
            }
        });
        if (custom != null)
        {
            MasterTemplateDeploy masterTemplateDeploy = new MasterTemplateDeploy(path, new ConnectableAddress(
                    CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost(),
                    CloudNetWrapper.getInstance().getWrapperConfig().getWebPort()
            ), CloudNetWrapper.getInstance().getSimpledUser(), CloudNetWrapper.getInstance().getOptionSet().has("ssl"), template, serverGroup.getName(), custom);
            try
            {
                masterTemplateDeploy.deploy();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        } else if (x != null && x.getBackend().equals(TemplateResource.MASTER))
        {
            MasterTemplateDeploy masterTemplateDeploy = new MasterTemplateDeploy(path, new ConnectableAddress(
                    CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost(),
                    CloudNetWrapper.getInstance().getWrapperConfig().getWebPort()
            ), CloudNetWrapper.getInstance().getSimpledUser(), CloudNetWrapper.getInstance().getOptionSet().has("ssl"), template, serverGroup.getName(), custom);

            try
            {
                masterTemplateDeploy.deploy();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        } else
        {
            System.out.println("Copying template from " + this.serverProcess.getMeta().getServiceId() + " to local directory...");

            try
            {
                FileUtility.copyFilesInDirectory(new File(this.path), new File("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName()));
                FileUtility.deleteDirectory(new File("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + serverProcess.getMeta().getTemplate().getName() + "/CLOUD"));
                new File("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + serverProcess.getMeta().getTemplate().getName() + "/plugins/CloudNetAPI.jar").delete();
            } catch (Exception e)
            {
            }

            System.out.println("Template " + template.getName() + " was copied!");
        }
    }

    @Override
    public String toString()
    {
        return "[" + serverProcess.getMeta().getServiceId().getServerId() + "/port=" + serverProcess.getMeta().getPort() + "/memory=" + serverProcess.getMeta().getMemory() + "]";
    }

    public void kill()
    {
        if (instance.isAlive())
        {
            executeCommand("stop");
            NetworkUtils.sleepUninterruptedly(500);
        }

        instance.destroyForcibly();
    }

    public void copyDirectory(String name)
    {
        File file = new File(path, name);

        if (file.exists() && file.isDirectory())
        {
            try
            {
                FileUtility.copyFilesInDirectory(file, new File("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + serverProcess.getMeta().getTemplate().getName() + NetworkUtils.SLASH_STRING + name));
            } catch (IOException e)
            {
            }
        }
    }

    private void startProcess() throws Exception
    {
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("java ");
        for (String command : serverProcess.getMeta().getProcessParameters())
            commandBuilder.append(command).append(NetworkUtils.SPACE_STRING);

        for (String command : serverProcess.getMeta().getTemplate().getProcessPreParameters())
            commandBuilder.append(command).append(NetworkUtils.SPACE_STRING);

        commandBuilder.append("-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:MaxPermSize=256M -XX:-UseAdaptiveSizePolicy -XX:CompileThreshold=100 -Dcom.mojang.eula.agree=true -Dio.netty.recycler.maxCapacity=0 -Dio.netty.recycler.maxCapacity.default=0 -Djline.terminal=jline.UnsupportedTerminal -Xmx" +
                serverProcess.getMeta().getMemory() + "M -jar ");

        switch (serverGroup.getServerType())
        {
            case CAULDRON:
                commandBuilder.append("cauldron.jar nogui");
                break;
            case GLOWSTONE:
                commandBuilder.append("glowstone.jar nogui");
                break;
            case CUSTOM:
                commandBuilder.append("minecraft_server.jar nogui");
                break;
            default:
                commandBuilder.append("spigot.jar nogui");
                break;
        }

        this.instance = Runtime.getRuntime().exec(commandBuilder.toString().split(NetworkUtils.SPACE_STRING), null, new File(path));
    }

    private boolean a() throws Exception
    {
        Files.createDirectories(dir);

        {
            Template template = this.serverGroup.getGlobalTemplate();
            if (custom != null)
            {
                MasterTemplateLoader templateLoader = new MasterTemplateLoader(new StringBuilder(
                        CloudNetWrapper.getInstance().getOptionSet().has("ssl") ? "https://" : "http://"
                )
                        .append(CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost())
                        .append(":")
                        .append(CloudNetWrapper.getInstance().getWrapperConfig().getWebPort())
                        .append("/cloudnet/api/v1/download").toString()
                        , dir.toString() + "/template.zip", CloudNetWrapper.getInstance().getSimpledUser(), template, serverGroup.getName(), custom);
                System.out.println("Downloading template for " + this.serverProcess.getMeta().getServiceId().getGroup());
                templateLoader.load();
                templateLoader.unZip(dir.toString());
                FileUtility.copyFilesInDirectory(new File(dir.toString()), new File(path));
            } else if (template.getBackend().equals(TemplateResource.URL) && template.getUrl() != null)
            {
                String groupTemplates = "local/cache/web_templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName();
                if (!Files.exists(Paths.get(groupTemplates)))
                {
                    Files.createDirectories(Paths.get(groupTemplates));
                    TemplateLoader templateLoader = new TemplateLoader(template.getUrl(), groupTemplates + "/template.zip");
                    System.out.println("Downloading template for " + this.serverProcess.getMeta().getServiceId().getGroup());
                    templateLoader.load();
                    templateLoader.unZip(groupTemplates);
                }

                FileUtility.copyFilesInDirectory(new File(groupTemplates), new File(path));
            } else if (template.getBackend().equals(TemplateResource.MASTER) && CloudNetWrapper.getInstance().getSimpledUser() != null)
            {
                String groupTemplates = "local/cache/web_templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName();
                if (!Files.exists(Paths.get(groupTemplates)))
                {
                    Files.createDirectories(Paths.get(groupTemplates));
                    MasterTemplateLoader templateLoader = new MasterTemplateLoader(new StringBuilder(
                            CloudNetWrapper.getInstance().getOptionSet().has("ssl") ? "https://" : "http://"
                    )
                            .append(CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost())
                            .append(":")
                            .append(CloudNetWrapper.getInstance().getWrapperConfig().getWebPort())
                            .append("/cloudnet/api/v1/download").substring(0)
                            , groupTemplates + "/template.zip", CloudNetWrapper.getInstance().getSimpledUser(), template, serverGroup.getName(), custom);
                    System.out.println("Downloading template for " + this.serverProcess.getMeta().getServiceId().getGroup());
                    templateLoader.load();
                    templateLoader.unZip(groupTemplates);
                }
                FileUtility.copyFilesInDirectory(new File(groupTemplates), new File(path));
            } else if (Files.exists(Paths.get("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName())))
            {

                FileUtility.copyFilesInDirectory(new File("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName()), new File(path));
            } else
            {
            }
        }

        if (serverProcess.getMeta().getUrl() != null)
        {
            if (!Files.exists(Paths.get(path + "/plugins")))
                Files.createDirectory(Paths.get(path + "/plugins"));

            TemplateLoader templateLoader = new TemplateLoader(serverProcess.getMeta().getUrl(), path + "/template.zip");
            System.out.println("Downloading template for " + this.serverProcess.getMeta().getServiceId().getServerId());
            templateLoader.load();
            templateLoader.unZip(path);
        } else
        {

            if (!Files.exists(Paths.get(path + "/plugins")))
                Files.createDirectory(Paths.get(path + "/plugins"));

            if (serverGroup.getTemplates().size() == 0) return false;

            Template template = this.serverProcess.getMeta().getTemplate();
            if (template.getBackend().equals(TemplateResource.URL) && template.getUrl() != null)
            {
                String groupTemplates = "local/cache/web_templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName();
                if (!Files.exists(Paths.get(groupTemplates)))
                {
                    Files.createDirectories(Paths.get(groupTemplates));
                    TemplateLoader templateLoader = new TemplateLoader(template.getUrl(), groupTemplates + "/template.zip");
                    System.out.println("Downloading template for " + this.serverProcess.getMeta().getServiceId().getGroup());
                    templateLoader.load();
                    templateLoader.unZip(groupTemplates);
                }
                FileUtility.copyFilesInDirectory(new File(groupTemplates), new File(path));
            } else if (template.getBackend().equals(TemplateResource.MASTER) && CloudNetWrapper.getInstance().getSimpledUser() != null)
            {
                String groupTemplates = "local/cache/web_templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName();
                if (!Files.exists(Paths.get(groupTemplates)))
                {
                    Files.createDirectories(Paths.get(groupTemplates));
                    MasterTemplateLoader templateLoader = new MasterTemplateLoader(new StringBuilder(
                            CloudNetWrapper.getInstance().getOptionSet().has("ssl") ? "https://" : "http://"
                    )
                            .append(CloudNetWrapper.getInstance().getWrapperConfig().getCloudnetHost())
                            .append(":")
                            .append(CloudNetWrapper.getInstance().getWrapperConfig().getWebPort())
                            .append("/cloudnet/api/v1/download").substring(0)
                            , groupTemplates + "/template.zip", CloudNetWrapper.getInstance().getSimpledUser(), template, serverGroup.getName(), custom);
                    System.out.println("Downloading template for " + this.serverProcess.getMeta().getServiceId().getGroup());
                    templateLoader.load();
                    templateLoader.unZip(groupTemplates);
                }
                FileUtility.copyFilesInDirectory(new File(groupTemplates), new File(path));
            } else if (Files.exists(Paths.get("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName())))
            {

                FileUtility.copyFilesInDirectory(new File("local/templates/" + serverGroup.getName() + NetworkUtils.SLASH_STRING + template.getName()), new File(path));
            } else
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public ServiceId getServiceId()
    {
        return serverProcess.getMeta().getServiceId();
    }
}