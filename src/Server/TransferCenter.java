package Server;



import CommonClasses.CommandsData;
import CommonClasses.DataBlock;
import Server.MainModulsThreads.ProcessingRequestThread;
import Server.MainModulsThreads.ReadRequestThread;
import Server.MainModulsThreads.SendingAnswerThread;
import Server.OptionalThrows.ConnectionRequestsChecker;
//import CommonClasses.DataBlock;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransferCenter {

//    65535

    Selector selector;
    DatagramChannel mainServerDatagramChannel;
    WorkWithUser workWithUser;

    ConcurrentLinkedQueue<DataPacket> requestsWaitingProcessing = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataPacket> answersWaitingSending = new ConcurrentLinkedQueue<>();
    ExecutorService service = Executors.newCachedThreadPool();


    private final static int SIZEOFBUFFER = 500;

    public TransferCenter(WorkWithUser workWithUser){
        this.workWithUser = workWithUser;

        System.out.println("Введите 0, если хотите автоматически создать сервер или 1, если хотите привязать его к определённому порту:");
        if(Integer.valueOf(new Scanner(System.in).nextLine()).equals(1)){
            System.out.println("Ведите порт:");
            mainServerDatagramChannel = createNewChannelWithIP(Integer.valueOf(new Scanner(System.in).nextLine()));
        }else {
            mainServerDatagramChannel = createNewChannelWithIP();
        }
        writeInformationAboutServer();
        try {
            selector = Selector.open();
        } catch (IOException e) {
            System.out.println("Проблемы с созданием селектора!");
            e.printStackTrace();
        }
        //Создание чеккера, при обращении к IP которого будет устанавливаться соединение с USER-ом
        ConnectionRequestsChecker connectionRequestsChecker = new ConnectionRequestsChecker(selector, mainServerDatagramChannel);
        connectionRequestsChecker.start();
    }


    public static DatagramChannel createNewChannelWithIP() {
        Random random = new Random();
        DatagramChannel datagramChannel = null;
        try {
            datagramChannel = DatagramChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int port = -1;

        boolean workingPort = false;
        while (!workingPort) {
            port = random.nextInt(65535);

            try {
                datagramChannel.bind(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), port));
                workingPort = true;
            } catch (IOException e) { }
        }
        return datagramChannel;
    }

    public static DatagramChannel createNewChannelWithIP(int port) {
//        Random random = new Random();
        DatagramChannel datagramChannel = null;
        try {
            datagramChannel = DatagramChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
        }


            try {
                datagramChannel.bind(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), port));
            } catch (IOException e) {
                System.out.println("Некорректный или занятый port введите другой:");
                datagramChannel = createNewChannelWithIP((new Scanner(System.in)).nextInt());
            }
        return datagramChannel;
    }

    public void writeInformationAboutServer() {
        System.out.println("IP сервера: " + mainServerDatagramChannel.socket().getLocalAddress().getHostAddress()+ "\nPort сервера: " + mainServerDatagramChannel.socket().getLocalPort() + "\n");
    }

//    /**Processing requests from different users and started work with them*/
//    public void requestsProcessing(){
//        while (true){
//
//
//            try {
//                if(selector.selectNow() == 0){
//                    continue;
//                }
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            Set<SelectionKey> selectionKeys = selector.selectedKeys();
//            Iterator iterator = selectionKeys.iterator();
//
//            while (iterator.hasNext()){
//                SelectionKey selectionKey = (SelectionKey) iterator.next();
//                iterator.remove();
//
//                Object obj = null;
//                DatagramChannel selectedDatagramChannel = null;
//                try {
//                    selectedDatagramChannel = DatagramChannel.open();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                try {
//                    DatagramChannel datagramChannel = (DatagramChannel) selectionKey.channel();
//                    obj = receiveObject(datagramChannel);
//
//                    selectedDatagramChannel.connect(((DatagramChannel) selectionKey.channel()).getRemoteAddress());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//                CommandsData commandsData = null;
//                DataBlock dataBlock = (DataBlock) obj;
//                commandsData = dataBlock.getCommandsData();
//                TransferCenter.copyFieldsFromTo(dataBlock, commandsData);
//
//                workWithUser.startWorkWithUser(selectedDatagramChannel, commandsData);
//            }
//        }
//    }


//    Ключи, обработка которых уже ведётся в другом потоке
//    Stack<SelectionKey> processingSelectionKeys = new Stack<>();
    ConcurrentLinkedQueue<SelectionKey> processingSelectionKeys = new ConcurrentLinkedQueue<>();
    /**Processing requests from different users and started work with them*/
    public void requestsProcessing(){

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();

        while (true){

            try {

                lock.lock();
                if(selector.selectNow()!=0){

                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator iterator = selectionKeys.iterator();
//                    System.out.println("Количество выбранных ключей: " + selectionKeys.size());
                    while (iterator.hasNext()){
//                        System.out.println("Выбрали колюч");
                        SelectionKey selectionKey = (SelectionKey) iterator.next();
                        iterator.remove();


                        Iterator OSKiterator = processingSelectionKeys.iterator();
                        boolean keyAlreadyInProcessing = false;


//                        Проверка не был ли этот ключ уже запущен в обработку.
//                        Необходима, тк из-за разницы скорости выполнения потоков может запустиься один и тот же запрос несколько раз
                        while (OSKiterator.hasNext()){
                            if(selectionKey.equals(OSKiterator.next())){
                                keyAlreadyInProcessing = true;
//                                System.out.println("Ключ уже в обработке");
                                break;
                            }
                        }


                        if(!keyAlreadyInProcessing){
//                            System.out.println("Перешли к обработке ключа");
                            processingSelectionKeys.add(selectionKey);
                            ReadRequestThread readRequestThread = new ReadRequestThread(selectionKey, requestsWaitingProcessing, SIZEOFBUFFER, processingSelectionKeys, lock, condition);
                            service.execute(readRequestThread);
//                            System.out.println("find");
//                            System.out.println("Конец итерации цикла");

//                            System.out.println("find" + selectionKey.hashCode());
//                            System.out.println("processingSelectionKeys is empty :" + processingSelectionKeys.isEmpty());
                        }

                    }
                }
                condition.signalAll();
                lock.unlock();
//                System.out.println("tt");
//                condition.signalAll();
//                lock.unlock();
//                Thread.sleep(500);

            } catch (Exception e) {
                e.printStackTrace();
            }

            while (!requestsWaitingProcessing.isEmpty()){
//                System.out.println("process");
                ProcessingRequestThread processingRequestThread = new ProcessingRequestThread(answersWaitingSending, requestsWaitingProcessing.poll(),workWithUser);
                processingRequestThread.fork();
            }

            while (!answersWaitingSending.isEmpty()){
//                System.out.println("send");
                DataPacket dataPacket = answersWaitingSending.poll();
                service.execute(new SendingAnswerThread(dataPacket));
            }
        }
    }



    public static Object receiveObject(DatagramChannel datagramChannel) throws IOException {


        Object obj = null;
        boolean endOfReceive = false;
        byte[] objByteArr = new byte[0];
        byte[] receivedArr;
//        int errCount = 0;

        while (!endOfReceive){
            receivedArr = receiveByteArr(datagramChannel);

            boolean onlyNuls = true;
            for(byte b : receivedArr){
                if(b != 0){
                    onlyNuls = false;
                }
            }
            if(onlyNuls){
                continue;
            }
            byte[] newArr = new byte[objByteArr.length + receivedArr.length];

            for(int i =0;i<(objByteArr.length + receivedArr.length);i++){
                if(i<objByteArr.length){
                    newArr[i] = objByteArr[i];
                }
                else {
                    newArr[i] = receivedArr[i-objByteArr.length];
                }
            }
            objByteArr = newArr;

//            errCount++;
//            if(errCount >3){
//                System.out.println("size: " + objByteArr.length);
//                for(int i = 0; i< objByteArr.length; i++){
//                    System.out.println(i + " " +objByteArr[i]);
//                }
//                System.exit(1);
//            }

            try {
                obj = ObjectProcessing.deSerializeObject(objByteArr);
                endOfReceive = true;

            }catch (Exception e){}
        }
//        System.out.println("errCount: " + errCount);
        return obj;
    }

    public static byte[] receiveByteArr(DatagramChannel datagramChannel) {
        final int size = SIZEOFBUFFER;
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[size]);
        byte[] bytes = null;
        try {
            datagramChannel.receive(byteBuffer);
            bytes = byteBuffer.array();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes;
    }

    private static void copyFieldsFromTo(CommandsData commandsData, DataBlock dataBlock){
        dataBlock.setCommandWithElementParameter(commandsData.isCommandWithElementParameter());
        dataBlock.setCreator(commandsData.getCreator());
        dataBlock.setParameter(commandsData.getParameter());
        dataBlock.setFlat(commandsData.getFlat());
        dataBlock.setBufferedReader(commandsData.getBufferedReader());
        dataBlock.setOpeningFiles(commandsData.getOpeningFiles());
        dataBlock.setCommandEnded(commandsData.isCommandEnded());
        dataBlock.setPhrase(commandsData.getPhrase());
        dataBlock.setServerNeedStringParameter(commandsData.isServerNeedStringParameter);
        dataBlock.setServerNeedElementParameter(commandsData.isServerNeedElementParameter);
        dataBlock.setUserNeedToShowFlatArr(commandsData.isUserNeedToShowFlatArr);
        dataBlock.setFlats(commandsData.getFlats());

    }

    public static void copyFieldsFromTo(DataBlock dataBlock, CommandsData commandsData){
        commandsData.setCommandWithElementParameter(dataBlock.isCommandWithElementParameter());
        commandsData.setCreator(dataBlock.getCreator());
        commandsData.setParameter(dataBlock.getParameter());
        commandsData.setFlat(dataBlock.getFlat());
        commandsData.setBufferedReader(dataBlock.getBufferedReader());
        commandsData.setOpeningFiles(dataBlock.getOpeningFiles());
        commandsData.setCommandEnded(dataBlock.isCommandEnded());
        commandsData.setPhrase(dataBlock.getPhrase());
        commandsData.setServerNeedStringParameter(dataBlock.isServerNeedStringParameter);
        commandsData.setServerNeedElementParameter(dataBlock.isServerNeedElementParameter);
        commandsData.setUserNeedToShowFlatArr(dataBlock.isUserNeedToShowFlatArr);
        commandsData.setFlats(dataBlock.getFlats());

    }

    public  static void sendAnswerToUser(DatagramChannel datagramChannel, CommandsData commandsData){
        DataBlock dataBlock = new DataBlock();
        BufferedReader bufferedReader = commandsData.getBufferedReader();
        commandsData.setBufferedReader(null);
        copyFieldsFromTo(commandsData, dataBlock);
        dataBlock.setCommandsData(commandsData);
        try {
            sendObject(datagramChannel, dataBlock);
        } catch (IOException e) {
            e.printStackTrace();
        }
//            ByteBuffer byteBuffer = ByteBuffer.wrap(ObjectProcessing.serializeObject(dataBlock));
//            datagramChannel.send(byteBuffer, datagramChannel.socket().getRemoteSocketAddress());
        commandsData.setBufferedReader(bufferedReader);

    }

    public static void sendObject(DatagramChannel datagramChannel, Object object) throws IOException {

            sendByteArr(ObjectProcessing.serializeObject(object), datagramChannel);

    }

    private static void sendByteArr(byte[] bArr, DatagramChannel datagramChannel){
//        System.out.println(bArr.length);
//        if(bArr.length>500){
//            System.out.println(bArr[602]);
//        }
        final int size = SIZEOFBUFFER;
        byte[] bigArr = new byte[bArr.length + size - (bArr.length % size)];
        for (int i =0; i < bArr.length; i++){
            bigArr[i] = bArr[i];
        }
        bArr = bigArr;


        for(int i = 0; i < Math.ceil(Float.valueOf(bArr.length)/size); i++){
            byte[] data = new byte[size];
            for (int j = 0; j<(size);j++){
                data[j] = bArr[j+(size)*i];
            }

//            DatagramPacket datagramPacket = new DatagramPacket(data, size, datagramChannel.getRemoteAddress());
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);
            try {
                datagramChannel.send(byteBuffer, datagramChannel.getRemoteAddress());
//                datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                System.out.println("Проблема с отправкой объекта!");
            }
        }
    }










//
//    public void writeInformation(){
//        System.out.println("IP-aдрес сервера: " + socketAddressReceive.getAddress().getHostAddress());
//        System.out.println("Порт сервера: " + socketAddressReceive.getPort());
//    }
//
//    public void createConnectionForSending(DataBlock dataBlock){
//        String[] parametersForSendingProcesses = dataBlock.parameter.split("; ");
//        socketAddressForSend = new InetSocketAddress(parametersForSendingProcesses[0], Integer.valueOf(parametersForSendingProcesses[1]));
//        try {
//            datagramChannelForSend = DatagramChannel.open();
//            datagramChannelForSend.bind(null);
//        } catch (IOException e) {
//            System.out.println("Проблема с созданием канала!");
//            e.printStackTrace();
//        }
//        DataBlock allIsRightData = new DataBlock();
//        allIsRightData.allRight = true;
//
//        sendObjectToUser(allIsRightData);
//    }
//
//    public <T> void sendObjectToUser(T object){
//        byte[] serObject = serializeObject(object);
//        DataBlock warningAboutSize = new DataBlock();
//        warningAboutSize.parameter = String.valueOf(serObject.length);
//        sendByteArr(serializeObject(warningAboutSize));
//        //Необходимо получать allRight --- доделать
//        sendByteArr(serializeObject(object));
//    }
//
//    private void sendByteArr(byte[] bArr){
//        ByteBuffer buffer = ByteBuffer.wrap(bArr);
//        try {
//            datagramChannelForSend.send(buffer, socketAddressForSend);
//            buffer.clear();
//        } catch (IOException e) {
//            System.out.println("Проблема с отправкой через канал!");
//            e.printStackTrace();
//        }
//    }
//
//    public static <T> byte[] serializeObject(T obj){
//        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//        ObjectOutputStream objectOutputStream = null;
//        try {
//            objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
//        } catch (IOException e) {
//            System.out.println("Проблема с созданием потока для серилизации объектов!");
//        }
//        byte[] serObj = null;
//
//        try {
//            objectOutputStream.writeObject(obj);
//            serObj = byteArrayOutputStream.toByteArray();
//        } catch (IOException e) {
//            System.out.println("Проблема с серелизацией объекта для отправки на сервер!");
//            e.printStackTrace();
//        }
//
//        return serObj;
//    }
//
//    private int createSocketAddressForReceive(){
//        Random random = new Random();
//
//        int port = -1;
//
//        boolean workingSocket = false;
//        while (!workingSocket) {
//            port = random.nextInt(65535);
//
//            try {
//                socketAddressReceive = new InetSocketAddress(InetAddress.getLocalHost(), port);
//                datagramSocketReceive = new DatagramSocket(port);
//                workingSocket = true;
//            } catch (/*UnknownHostException | */SocketException | UnknownHostException e) {
//                System.out.println("Проблема с созданием порта!");
//                e.printStackTrace();
//            }
//
//        }
//        return port;
//    }
//
////    public static InetSocketAddress getServerSocketAddress() {
////        return serverSocketAddress;
////    }
////
////    /**Считывает запрос и возвращает CommandsData объект*/
////    public CommandsData checkingRequests(){
////        byte[] bArr = new byte[76];
////        DatagramPacket datagramPacket = new DatagramPacket(bArr, bArr.length);
////        try {
////            datagramSocket.receive(datagramPacket);
////        } catch (IOException e) {
////            System.out.println("Проблема с получением файла!");
////        }
////        InputStream inputStream = new ByteArrayInputStream(bArr);
////
////        ObjectInputStream objectInputStream = null;
////        try {
////            objectInputStream = new ObjectInputStream(inputStream);
////        } catch (IOException e) {
////            System.out.println("Проблема с созданием ObjectInputStream!");
////        }
////
////
////        for(byte b : bArr){
////            System.out.println(b);
////        }
////
////
////        CommandsData commandsData = null;
////        try{
////            commandsData = (CommandsData) objectInputStream.readObject();
////        }catch (Exception e){
////            System.out.println("Проблема с десерелизацией объекта!");
////        }
////        return commandsData;
////    }
////
////    /**Считывает запрос и возвращает CommandsData объект*/
////    public CommandsData checkingRequests(){
////        byte[] bArr = new byte[200];
////        DatagramPacket datagramPacket = new DatagramPacket(bArr, bArr.length);
////        try {
////            datagramSocket.receive(datagramPacket);
////        } catch (IOException e) {
////            System.out.println("Проблема с получением файла!");
////        }
////        InputStream inputStream = new ByteArrayInputStream(bArr);
////
////        ObjectInputStream objectInputStream = null;
////        try {
////            objectInputStream = new ObjectInputStream(inputStream);
////        } catch (IOException e) {
////            System.out.println("Проблема с созданием ObjectInputStream!");
////        }
////
////        CommonClasses.CommandsData commandsData = null;
////
////        try {
////            commandsData = (CommonClasses.CommandsData) objectInputStream.readObject();
////        } catch (IOException e) {
////            e.printStackTrace();
////        } catch (ClassNotFoundException e) {
////            e.printStackTrace();
////        }
//////        System.out.println(commandsData.name());
//////        commandsData.show();
////        return commandsData;
////    }
////
////    public void sendAnswerToUser(AbstractDataBlock answerToUser){
////        try {
////            byte[] bArr = new byte[1000];
////            DatagramChannel datagramChannel = DatagramChannel.open();
////            SocketAddress socketAddress = new InetSocketAddress("192.168.1.135", 6667);
////            datagramChannel.connect(socketAddress);
////            ByteBuffer byteBuffer = ByteBuffer.wrap(bArr);
////            byteBuffer.flip();
////            datagramChannel.send(byteBuffer, socketAddress);
////            System.out.println("Send == true");
////        } catch (IOException e) {
////            System.out.println("Проблема с каналом для обмена!");
////        }
////    }
//
//    /**считывает object в массив неизвестной длины*/
//    public Object receiveObjectFromUser(){
//
////        CommandsData commandsData = (CommandsData) receiveObjectFromUser(receiveObjectArrSize());
////        commandsData.getFlat().show();
////        return commandsData;
//
//
//        Object object = receiveObjectFromUser(receiveObjectArrSize());
//        try {
//            DataBlock dataBlock = (DataBlock) object;
//            if(dataBlock.getParameter().equals("checkingConnect") & dataBlock.isAllRight()){
//                sendObjectToUser(dataBlock);
//                object = receiveObjectFromUser();
//            }
//        }catch (Exception e){
//        }
//
//        return object;
//
//
//
//
//
//
////        byte[] objectByteArr = new byte[receiveObjectArrSize()];
//
////        DatagramPacket datagramPacket = new DatagramPacket(objectByteArr, objectByteArr.length);
//
//
////        byte[] objectByteArr = new byte[200];
////        DatagramPacket datagramPacket = new DatagramPacket(objectByteArr, objectByteArr.length);
////        try {
////            datagramSocket.receive(datagramPacket);
////        } catch (IOException e) {
////            System.out.println("Проблема с получением файла!");
////        }
////        InputStream inputStream = new ByteArrayInputStream(objectByteArr);
////
////        ObjectInputStream objectInputStream = null;
////        try {
////            objectInputStream = new ObjectInputStream(inputStream);
////        } catch (IOException e) {
////            System.out.println("Проблема с созданием ObjectInputStream!");
////        }
////
////        CommonClasses.CommandsData commandsData = null;
////
////        try {
////            commandsData = (CommonClasses.CommandsData) objectInputStream.readObject();
////        } catch (IOException e) {
////            e.printStackTrace();
////        } catch (ClassNotFoundException e) {
////            e.printStackTrace();
////        }
//////        System.out.println(commandsData.name());
//////        commandsData.show();
////        return commandsData;
//    }
//
//    /**считывает object в массив уже известной длины*/
//    private Object receiveObjectFromUser(int objectArrSize){
//
//        byte[] objectByteArr = new byte[objectArrSize];
//        DatagramPacket datagramPacket = new DatagramPacket(objectByteArr, objectByteArr.length);
//
//        try {
//            datagramSocketReceive.receive(datagramPacket);
//        } catch (IOException e) {
//            System.out.println("Проблема с получением файла!");
//            e.printStackTrace();
//        }
//        return deSerialize(objectByteArr);
//    }
//
//    public static Object deSerializeObject(byte[] objectByteArr){
//        ByteArrayInputStream inputStream = new ByteArrayInputStream(objectByteArr);
//
//        ObjectInputStream objectInputStream = null;
//
//        try {
//            objectInputStream = new ObjectInputStream(inputStream);
//        } catch (IOException e) {
//            System.out.println("Проблема с созданием ObjectInputStream!");
//            e.printStackTrace();
//        }
//
//        Object object = null;
//        try {
//            object = objectInputStream.readObject();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//        return object;
//    }
//
//    /**ждёт от юзера объект, в параметре которого будет размер следующего объекта и возвращает этот размер*/
//    private int receiveObjectArrSize(){
//        AbstractDataBlock objectWithSizeParameter = (AbstractDataBlock) receiveObjectFromUser(500);
//        return Integer.valueOf(objectWithSizeParameter.parameter);
//    }

}
