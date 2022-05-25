package pubsub;

import digital.twin.CommandManager;
import digital.twin.CommandResultManager;
import digital.twin.DTUseFacade;
import digital.twin.OutputSnapshotsManager;
import plugin.DriverConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import utils.DTLogger;

/**
 * @author Paula Muñoz, Daniel Pérez - University of Málaga
 * Class used by a SubService to listen to events generated by a PubService.
 */
public class DTPubSub extends JedisPubSub {

    public static final String DT_OUT_CHANNEL = "DTOutChannel";
    public static final String COMMAND_OUT_CHANNEL = "CommandOutChannel";
    public static final String COMMAND_IN_CHANNEL = "CommandInChannel";
    public static final String TIME_CHANNEL = "TimeChannel";

    private final Jedis jedis;
    private final OutputSnapshotsManager dtOutSnapshotsManager;
    private final CommandManager commandManager;
    private final CommandResultManager commandResultManager;
    private final DTUseFacade useApi;

    /**
     * Default constructor.
     * @param useApi USE API facade instance to interact with the currently displayed object diagram
     * @param jedis An instance of the Jedis client to access the data lake.
     */
    public DTPubSub(DTUseFacade useApi, Jedis jedis) {
        this.jedis = jedis;
        dtOutSnapshotsManager = new OutputSnapshotsManager(useApi);
        commandManager = new CommandManager(useApi);
        commandResultManager = new CommandResultManager(useApi);
        this.useApi = useApi;
    }

    /**
     * This method is called every time a message is received through a specific channel.
     * @param channel Channel from which the message was received
     * @param message Message received
     */
    @Override
    public void onMessage(String channel, String message) {
        try {
            switch (channel) {

                case DT_OUT_CHANNEL: // Info leaving USE
                    dtOutSnapshotsManager.saveObjectsToDataLake(jedis);
                    DTLogger.info("New Output Snapshots saved");
                    break;

                case COMMAND_IN_CHANNEL: // Commands entering USE
                    commandManager.saveObjectsToUseModel(jedis);
                    DTLogger.info("New Commands received");
                    break;

                case COMMAND_OUT_CHANNEL: // Command results leaving USE
                    commandResultManager.saveObjectsToDataLake(jedis);
                    DTLogger.info("New Command Results saved");
                    break;

                case TIME_CHANNEL: // Update USE model's timestamp
                    int dlTime = TimePubService.getDTTimestampInDataLake(jedis);
                    int useTime = useApi.getCurrentTime();
                    int ticks = (dlTime - useTime) / DriverConfig.TICK_PERIOD_MS;
                    if (ticks > 0) {
                        useApi.advanceTime(ticks);
                    }
                    break;

                default:
                    DTLogger.warn("Received message in unknown channel: " + channel);
                    break;

            }
        } catch (Exception ex) {
            DTLogger.error("An error ocurred on channel " + channel  + ":", ex);
        }
    }

    /**
     * This method is called every time a process subscribes to one of the channels.
     * @param channel Channel to which to subscribe
     * @param subscribedChannels Channel identifier
     */
    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        DTLogger.info("Client is subscribed to channel " + channel);
    }

}
