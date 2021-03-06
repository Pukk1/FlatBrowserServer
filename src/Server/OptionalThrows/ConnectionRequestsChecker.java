package Server.OptionalThrows;

import CommonClasses.FirstTimeConnectedData;
import Server.ObjectProcessing;
import Server.TransferCenter;


import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class ConnectionRequestsChecker extends Thread{

    Selector selector;
    DatagramChannel datagramChannel;

    public ConnectionRequestsChecker(Selector selector, DatagramChannel datagramChannel){
        this.datagramChannel = datagramChannel;
        this.selector = selector;
    }

    @Override
    public void run(){
        ByteBuffer byteBuffer = null;
        while (true){

            FirstTimeConnectedData firstTimeConnectedData = null;
            try {
                firstTimeConnectedData = (FirstTimeConnectedData) TransferCenter.receiveObject(datagramChannel);
            } catch (IOException e) {
                e.printStackTrace();
            }
            SocketAddress userSocketAddress = firstTimeConnectedData.getSocketAddress();

            SocketAddress socketAddress = null;
            try {
                socketAddress = firstTimeConnectedData.getSocketAddress();
                DatagramChannel newDatagramChannel = TransferCenter.createNewChannelWithIP();
                firstTimeConnectedData.setSocketAddress(newDatagramChannel.getLocalAddress());
                newDatagramChannel.connect(userSocketAddress);
                byteBuffer = ByteBuffer.wrap(ObjectProcessing.serializeObject(firstTimeConnectedData));
                datagramChannel.send(byteBuffer, socketAddress);
                newDatagramChannel.configureBlocking(false);
                newDatagramChannel.register(selector, SelectionKey.OP_READ);


            } catch (IOException e) {
                e.printStackTrace();
            }
//            System.out.println(selector.keys().size());
//            byteBuffer.clear();
        }
    }
}
