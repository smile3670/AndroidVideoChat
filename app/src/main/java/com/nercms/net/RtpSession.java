package com.nercms.net;

import android.util.Log;

import com.nercms.receive.Receive;
import com.nercms.send.Send;

import org.sipdroid.net.RtpPacket;
import org.sipdroid.net.RtpSocket;
import org.sipdroid.net.SipdroidSocket;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Created by weiqp on 2017/5/24.
 */

public class RtpSession {
    private int width;
    private int height;
    public RtpSession(String remoteIp, int remotePort, int width, int height){
        remote_ip = remoteIp;
        remote_port = remotePort;
        this.width = width;
        this.height = height;
    }

    private Send encode;      //编码器
    private Receive decode;   //解码器
    private RtpSocket rtp_socket = null; //创建RTP套接字
    private String remote_ip;
    private int remote_port;
    public void init(){
        //初始化解码器
        if (rtp_socket == null) {
            try {
                rtp_socket = new RtpSocket(new SipdroidSocket(19888), InetAddress.getByName(remote_ip), remote_port);
            }  catch (Exception e) {
                e.printStackTrace();
            }

            //解码
            rtp_receive_packet = new RtpPacket(socket_receive_Buffer, 0);
            decode = new Receive();
            decoder_handle = decode.CreateH264Packer(); //创建拼帧器
            decode.CreateDecoder(width, height); //创建解码器

            //编码
            rtp_send_packet = new RtpPacket(socket_send_Buffer, 0);
            encode = new Send();
            encoder_handle = encode.CreateEncoder(width, height); //调用底层函数，创建编码器
        }
    }

    private DecoderThread mDecoderThread;
    public void start(){
        mDecoderThread = new DecoderThread();
        mDecoderThread.start();
    }


    //接受 处理
    private RtpPacket rtp_receive_packet = null; //创建RTP接受包
    private long decoder_handle = 0; //拼帧器的句柄
    private byte[] frmbuf = new byte[65536]; //帧缓存
    private byte[] socket_receive_Buffer = new byte[2048]; //包缓存
    private byte[] buffer = new byte[2048];
    class DecoderThread extends Thread {
        public void run() {
            while (!isInterrupted()) {
                try {
                    rtp_socket.receive(rtp_receive_packet); //接收一个包
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int packetSize = rtp_receive_packet.getPayloadLength(); //获取包的大小

                if (packetSize <= 0)
                    continue;
                if (rtp_receive_packet.getPayloadType() != 2) //确认负载类型为2
                    continue;
                System.arraycopy(socket_receive_Buffer, 12, buffer, 0, packetSize); //socketBuffer->buffer
                int sequence = rtp_receive_packet.getSequenceNumber(); //获取序列号
                long timestamp = rtp_receive_packet.getTimestamp(); //获取时间戳
                int bMark = rtp_receive_packet.hasMarker() ? 1 : 0; //是否是最后一个包
                int frmSize = decode.PackH264Frame(decoder_handle, buffer, packetSize, bMark, (int) timestamp, sequence, frmbuf); //packer=拼帧器，frmbuf=帧缓存
                if (frmSize <= 0)
                    continue;

                decode.DecoderNal(frmbuf, frmSize, mDecoderCallback.getDecodeBuf());//解码后的图像存在mPixel中
                mDecoderCallback.onDecoderEnd();
            }

            //关闭
            if (decoder_handle != 0) {
                decode.DestroyH264Packer(decoder_handle);
                decoder_handle = 0;
            }
            if (rtp_socket != null) {
                rtp_socket.close();
                rtp_socket = null;
            }
            decode.DestoryDecoder();
        }
    }

    /**
     * 关闭摄像头 并释放资源
     */
    public void close() {
        mDecoderThread.interrupt();
        if (rtp_socket != null) {
            rtp_socket.close();
            rtp_socket = null;
        }
    }

    public interface DecodeCallback {
        void onDecoderEnd();
        byte[] getDecodeBuf();
    }

    public DecodeCallback mDecoderCallback;
    public void setDecodeCallback(DecodeCallback callback){
        mDecoderCallback = callback;
    }

    //发送
    private RtpPacket rtp_send_packet = null; //创建RTP发送包
    private long encoder_handle = -1; //创建编码器的句柄
    private int send_packetNum = 0; //包的数目
    private int[] send_packetSize = new int[200]; //包的尺寸
    private byte[] send_stream = new byte[65536]; //码流
    private byte[] socket_send_Buffer = new byte[65536]; //缓存 stream->socketBuffer->rtp_socket
    public void sendPreviewFrame(byte[] frame){
        if (encoder_handle != -1) {
            //底层函数，返回包的数目，返回包的大小存储在数组packetSize中，返回码流在stream中
            send_packetNum = encode.EncoderOneFrame(encoder_handle, -1, frame, send_stream, send_packetSize);
            Log.d("log", "原始数据大小：" + frame.length + "  转码后数据大小：" + send_stream.length);
            if (send_packetNum > 0) {
                //通过RTP协议发送帧
                final int[] pos = {0}; //从码流头部开始取
                final long timestamp = System.currentTimeMillis(); //设定时间戳
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int sequence = 0; //初始化序列号
                        for (int i = 0; i < send_packetNum; i++) {
                            rtp_send_packet.setPayloadType(2);//定义负载类型，视频为2
                            rtp_send_packet.setMarker(i == send_packetNum - 1 ? true : false); //是否是最后一个RTP包
                            rtp_send_packet.setSequenceNumber(sequence++); //序列号依次加1
                            rtp_send_packet.setTimestamp(timestamp); //时间戳
                            //Log.d("log", "序列号:" + sequence + " 时间：" + timestamp);
                            rtp_send_packet.setPayloadLength(send_packetSize[i]); //包的长度，packetSize[i]+头文件
                            //从码流stream的pos处开始复制，从socketBuffer的第12个字节开始粘贴，packetSize为粘贴的长度
                            System.arraycopy(send_stream, pos[0], socket_send_Buffer, 12, send_packetSize[i]); //把一个包存在socketBuffer中
                            pos[0] += send_packetSize[i]; //重定义下次开始复制的位置
                            Log.d("log", "序列号:" + sequence + " 时间：" + timestamp + "  send_packetSize=" + send_packetSize[i] + "  send_stream=" + send_stream.length);
                            try {
                                rtp_socket.send(rtp_send_packet);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }).start();
            }
        }
    }
}
