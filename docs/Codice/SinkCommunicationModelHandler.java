package it.unipi.cds.federatedLearning.sink;

import com.rabbitmq.client.Connection;
import it.unipi.cds.federatedLearning.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

public class SinkCommunicationModelHandler extends CommunicationModelHandler {

    private int nextID;
    private HashMap<Integer,Boolean> isNew;
    private boolean merging;

    public SinkCommunicationModelHandler(String hostname) {
        super(hostname);
        this.nextID = 1;
        this.isNew = new HashMap<>();
        this.merging = false;
    }
    @Override
    protected void initNodeToSink() {
        try {
            Connection connectionNodeSink = factory.newConnection();
            channelNodeSink = connectionNodeSink.createChannel();
            channelNodeSink.queueDeclare(NODE_TO_SINK_QUEUE_NAME, false, false, false, null);
            System.out.println("Waiting for models");

            receiver = new ModelReceiver(this);
            channelNodeSink.basicConsume(NODE_TO_SINK_QUEUE_NAME, true, receiver);
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void initSinkToNode() {
        try {
            Connection connectionSinkNode = factory.newConnection();
            channelSinkNode = connectionSinkNode.createChannel();
            channelSinkNode.exchangeDeclare(SINK_TO_NODE_EXCHANGE_NAME, "fanout", true);
            Log.info("SinkCommunicationModelHandler","Declaring SINK_TO_NODE_EXCHANGE");
        } catch (TimeoutException | IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void initRPC() {
        Thread rpcServer = new RPCServer(this, channelRPC, factory.getHost());
        rpcServer.start();
    }
    @Override
    public void receiveModel(Model deliveredModel) {
        deliveredModel.toFile(Config.PATH_SINK_RECEIVED_MODELS + deliveredModel.getNodeID() + ".json");
        deliveredModel.toFile( Config.PATH_SINK_HISTORY + "Node-" + deliveredModel.getNodeID() + "/" + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(new Date()) + ".json" );
        isNew.replace(deliveredModel.getNodeID(), true);

        if (areAllNew() && !merging) {
            // Start the merging of the models
            merging = true;
            ModelMerger mm = new ModelMerger(isNew.size(), this);
            mm.start();
        }
    }
    @Override
    public void sendModel() {
        try {
            Model model = new Model(-1, new String ( Files.readAllBytes( Paths.get(Config.PATH_SINK_MERGED_MODEL))));
            Log.info("Sink","Publishing " + model.toString() + " on SINK_TO_NODE_EXCHANGE");
            channelSinkNode.basicPublish(SINK_TO_NODE_EXCHANGE_NAME, "", null, model.getBytes());

            isNew.replaceAll((key, oldValue) -> Boolean.FALSE);

            merging = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private boolean areAllNew() {
        boolean and = true;
        for (Boolean b : isNew.values())
            and = and && b;
        return and;
    }
    public String registration() {
        isNew.put(nextID,false);
        updateFileSizePool();
        String nodeID = String.valueOf(nextID);
        Log.info("Sink", "New node requesting registration. New node id: " + nodeID + " | Current nodes: " + isNew.size());
        nextID++;
        return nodeID;
    }
    public String removeNode(int nodeID) {
        if (isNew.containsKey(nodeID)) {
            isNew.remove(nodeID);
            updateFileSizePool();
            Log.info("Sink", "Node "+ nodeID + " is leaving. Current nodes: " + isNew.size());
            return "OK";
        }
        Log.error("Sink", "Node "+ nodeID + " not found. Current nodes: " + isNew.size());
        return "NOT FOUND";
    }
    private void updateFileSizePool() {
        String nodeList = "";

        for ( int i : isNew.keySet()) {
            nodeList += Integer.valueOf(i);
            nodeList += ',';
        }
        if (!nodeList.equals(""))
            nodeList = nodeList.substring(0, nodeList.length() - 1);

        String filename = Config.PATH_SINK_POOL_SIZE + "nodeList.txt";
        File file = new File(filename);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            if (!file.exists())
                file.createNewFile();
            writer.flush();
            writer.write(nodeList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        try {
            new SinkCommunicationModelHandler(args[0]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.error("Sink", "Provide RabbitMQ Server's ip address as argument");
        }
    }


}
