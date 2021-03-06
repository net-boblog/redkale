/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.net.http.WebSocketPacket.FrameType;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.net.*;

/**
 * <blockquote><pre>
 * 一个WebSocket连接对应一个WebSocket实体，即一个WebSocket会绑定一个TCP连接。
 * WebSocket 有两种模式:
 *  1) 普通模式: 协议上符合HTML5规范, 其流程顺序如下:
 *      1.1 onOpen 若返回null，视为WebSocket的连接不合法，强制关闭WebSocket连接；通常用于判断登录态。
 *      1.2 createGroupid 若返回null，视为WebSocket的连接不合法，强制关闭WebSocket连接；通常用于判断用户权限是否符合。
 *      1.3 onConnected WebSocket成功连接后在准备接收数据前回调此方法。
 *      1.4 onMessage/onFragment+ WebSocket接收到消息后回调此消息类方法。
 *      1.5 onClose WebSocket被关闭后回调此方法。
 *
 *  此模式下 以上方法都应该被重载。
 *
 *  2) 原始二进制模式: 此模式有别于HTML5规范，可以视为原始的TCP连接。通常用于音频视频通讯场景。其流程顺序如下:
 *      2.1 onOpen 若返回null，视为WebSocket的连接不合法，强制关闭WebSocket连接；通常用于判断登录态。
 *      2.2 createGroupid 若返回null，视为WebSocket的连接不合法，强制关闭WebSocket连接；通常用于判断用户权限是否符合。
 *      2.3 onRead WebSocket成功连接后回调此方法， 由此方法处理原始的TCP连接， 同时业务代码去控制WebSocket的关闭。
 *
 *  此模式下 以上方法都应该被重载。
 * </pre></blockquote>
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
public abstract class WebSocket {

    //消息不合法
    public static final int RETCODE_SEND_ILLPACKET = 1 << 1; //2

    //ws已经关闭
    public static final int RETCODE_WSOCKET_CLOSED = 1 << 2; //4

    //socket的buffer不合法
    public static final int RETCODE_ILLEGALBUFFER = 1 << 3; //8

    //ws发送消息异常
    public static final int RETCODE_SENDEXCEPTION = 1 << 4; //16

    public static final int RETCODE_ENGINE_NULL = 1 << 5; //32

    public static final int RETCODE_NODESERVICE_NULL = 1 << 6; //64

    public static final int RETCODE_GROUP_EMPTY = 1 << 7; //128

    public static final int RETCODE_WSOFFLINE = 1 << 8; //256

    WebSocketRunner _runner; //不可能为空 

    WebSocketEngine _engine; //不可能为空 

    WebSocketGroup _group; //不可能为空 

    Serializable _sessionid; //不可能为空 

    Serializable _groupid; //不可能为空 

    SocketAddress _remoteAddress;//不可能为空 

    String _remoteAddr;//不可能为空 

    private final long createtime = System.currentTimeMillis();

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    protected WebSocket() {
    }

    //----------------------------------------------------------------
    /**
     * 发送消息体, 包含二进制/文本
     *
     * @param packet WebSocketPacket
     * @return 0表示成功， 非0表示错误码
     */
    public final int send(WebSocketPacket packet) {
        int rs = RETCODE_WSOCKET_CLOSED;
        if (this._runner != null) rs = this._runner.sendMessage(packet);
        if (_engine.finest) _engine.logger.finest("wsgroupid:" + getGroupid() + " send websocket result is " + rs + " on " + this + " by message(" + packet + ")");
        return rs;
    }

    /**
     * 发送单一的文本消息
     *
     * @param text 不可为空
     * @return 0表示成功， 非0表示错误码
     */
    public final int send(String text) {
        return send(text, true);
    }

    /**
     * 发送文本消息
     *
     * @param text 不可为空
     * @param last 是否最后一条
     * @return 0表示成功， 非0表示错误码
     */
    public final int send(String text, boolean last) {
        return send(new WebSocketPacket(text, last));
    }

    public final int sendPing() {
        //if (_engine.finest) _engine.logger.finest(this + " on "+_engine.getEngineid()+" ping...");
        return send(WebSocketPacket.DEFAULT_PING_PACKET);
    }

    public final int sendPing(byte[] data) {
        return send(new WebSocketPacket(FrameType.PING, data));
    }

    public final int sendPong(byte[] data) {
        return send(new WebSocketPacket(FrameType.PONG, data));
    }

    public final long getCreatetime() {
        return createtime;
    }

    /**
     * 发送单一的二进制消息
     *
     * @param data byte[]
     * @return 0表示成功， 非0表示错误码
     */
    public final int send(byte[] data) {
        return send(data, true);
    }

    /**
     * 发送二进制消息
     *
     * @param data 不可为空
     * @param last 是否最后一条
     * @return 0表示成功， 非0表示错误码
     */
    public final int send(byte[] data, boolean last) {
        return send(new WebSocketPacket(data, last));
    }

    /**
     * 发送消息, 消息类型是String或byte[]
     *
     * @param message 不可为空, 只能是String或者byte[]
     * @param last    是否最后一条
     * @return 0表示成功， 非0表示错误码
     */
    public final int send(Serializable message, boolean last) {
        return send(new WebSocketPacket(message, last));
    }

    //----------------------------------------------------------------
    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送文本消息
     *
     * @param groupid groupid
     * @param text    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, String text) {
        return sendEachMessage(groupid, text, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送二进制消息
     *
     * @param groupid groupid
     * @param data    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, byte[] data) {
        return WebSocket.this.sendEachMessage(groupid, data, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送文本消息
     *
     * @param groupid groupid
     * @param text    不可为空
     * @param last    是否最后一条
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, false, text, last);
    }

    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送二进制消息
     *
     * @param groupid groupid
     * @param data    不可为空
     * @param last    是否最后一条
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, false, data, last);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送文本消息
     *
     * @param groupid groupid
     * @param text    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, String text) {
        return sendRecentMessage(groupid, text, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送二进制消息
     *
     * @param groupid groupid
     * @param data    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, byte[] data) {
        return sendRecentMessage(groupid, data, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送文本消息
     *
     * @param groupid groupid
     * @param text    不可为空
     * @param last    是否最后一条
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, true, text, last);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送二进制消息
     *
     * @param groupid groupid
     * @param data    不可为空
     * @param last    是否最后一条
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, true, data, last);
    }

    private int sendMessage(Serializable groupid, boolean recent, String text, boolean last) {
        if (_engine.node == null) return RETCODE_NODESERVICE_NULL;
        int rs = _engine.node.sendMessage(groupid, recent, text, last);
        if (_engine.finest) _engine.logger.finest("wsgroupid:" + groupid + " " + (recent ? "recent " : "") + "send websocket result is " + rs + " on " + this + " by message(" + text + ")");
        return rs;
    }

    private int sendMessage(Serializable groupid, boolean recent, byte[] data, boolean last) {
        if (_engine.node == null) return RETCODE_NODESERVICE_NULL;
        int rs = _engine.node.sendMessage(groupid, recent, data, last);
        if (_engine.finest) _engine.logger.finest("wsgroupid:" + groupid + " " + (recent ? "recent " : "") + "send websocket result is " + rs + " on " + this + " by message(byte[" + data.length + "])");
        return rs;
    }

    /**
     * 获取在线用户的节点地址列表
     *
     * @param groupid groupid
     * @return 地址列表
     */
    protected final Collection<InetSocketAddress> getOnlineNodes(Serializable groupid) {
        return _engine.node.getOnlineNodes(groupid);
    }

    /**
     * 获取在线用户的详细连接信息
     *
     * @param groupid groupid
     * @return 地址集合
     */
    protected final Map<InetSocketAddress, List<String>> getOnlineRemoteAddress(Serializable groupid) {
        return _engine.node.getOnlineRemoteAddress(groupid);
    }

    /**
     * 获取当前WebSocket下的属性
     *
     * @param <T>  属性值的类型
     * @param name 属性名
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public final <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    /**
     * 移出当前WebSocket下的属性
     *
     * @param <T>  属性值的类型
     * @param name 属性名
     * @return 属性值
     */
    public final <T> T removeAttribute(String name) {
        return (T) attributes.remove(name);
    }

    /**
     * 给当前WebSocket下的增加属性
     *
     * @param name  属性值
     * @param value 属性值
     */
    public final void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * 获取当前WebSocket所属的groupid
     *
     * @return groupid
     */
    public final Serializable getGroupid() {
        return _groupid;
    }

    /**
     * 获取当前WebSocket的会话ID， 不会为null
     *
     * @return sessionid
     */
    public final Serializable getSessionid() {
        return _sessionid;
    }

    /**
     * 获取客户端直接地址, 当WebSocket连接是由代理服务器转发的，则该值固定为代理服务器的IP地址
     *
     * @return SocketAddress
     */
    public final SocketAddress getRemoteAddress() {
        return _remoteAddress;
    }

    /**
     * 获取客户端真实地址 同 HttpRequest.getRemoteAddr()
     *
     * @return String
     */
    public final String getRemoteAddr() {
        return _remoteAddr;
    }

    //-------------------------------------------------------------------
    /**
     * 获取当前WebSocket所属的WebSocketGroup， 不会为null
     *
     * @return WebSocketGroup
     */
    protected final WebSocketGroup getWebSocketGroup() {
        return _group;
    }

    /**
     * 获取指定groupid的WebSocketGroup, 没有返回null
     *
     * @param groupid groupid
     * @return WebSocketGroup
     */
    protected final WebSocketGroup getWebSocketGroup(Serializable groupid) {
        return _engine.getWebSocketGroup(groupid);
    }

    /**
     * 获取当前进程节点所有在线的WebSocketGroup
     *
     * @return WebSocketGroup列表
     */
    protected final Collection<WebSocketGroup> getWebSocketGroups() {
        return _engine.getWebSocketGroups();
    }

    //-------------------------------------------------------------------
    /**
     * 返回sessionid, null表示连接不合法或异常,默认实现是request.getSessionid(false)，通常需要重写该方法
     *
     * @param request HttpRequest
     * @return sessionid
     */
    public Serializable onOpen(final HttpRequest request) {
        return request.getSessionid(false);
    }

    /**
     * 创建groupid， null表示异常， 必须实现该方法， 通常为用户ID为groupid
     *
     * @return groupid
     */
    protected abstract Serializable createGroupid();

    /**
     * 标记为WebSocketBinary才需要重写此方法
     *
     * @param channel 请求连接
     */
    public void onRead(AsyncConnection channel) {
    }

    public void onConnected() {
    }

    public void onMessage(String text) {
    }

    public void onPing(byte[] bytes) {
    }

    public void onPong(byte[] bytes) {
    }

    public void onMessage(byte[] bytes) {
    }

    public void onFragment(String text, boolean last) {
    }

    public void onFragment(byte[] bytes, boolean last) {
    }

    public void onClose(int code, String reason) {
    }

    /**
     * 显式地关闭WebSocket
     */
    public final void close() {
        if (this._runner != null) this._runner.closeRunner();
    }

    @Override
    public String toString() {
        return "ws" + Objects.hashCode(this) + "@" + _remoteAddr;
    }
}
