package com.fangxuele.tool.push.logic.msgsender;

import com.fangxuele.tool.push.App;
import com.fangxuele.tool.push.logic.PushControl;
import com.fangxuele.tool.push.logic.msgmaker.WxMpTemplateMsgMaker;
import me.chanjar.weixin.common.util.http.apache.DefaultApacheHttpClientBuilder;
import me.chanjar.weixin.mp.api.WxMpInMemoryConfigStorage;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import java.util.concurrent.CountDownLatch;

/**
 * <pre>
 * 微信公众号模板消息发送器
 * </pre>
 *
 * @author <a href="https://github.com/rememberber">RememBerBer</a>
 * @since 2019/6/15.
 */
public class WxMpTemplateMsgSender implements IMsgSender {
    public volatile static WxMpInMemoryConfigStorage wxMpConfigStorage;
    public volatile static WxMpService wxMpService;
    private WxMpTemplateMsgMaker wxMpTemplateMsgMaker;

    public WxMpTemplateMsgSender() {
        wxMpTemplateMsgMaker = new WxMpTemplateMsgMaker();
        wxMpService = getWxMpService();
    }

    @Override
    public SendResult send(String[] msgData) {
        SendResult sendResult = new SendResult();

        try {
            String openId = msgData[0];
            WxMpTemplateMessage wxMessageTemplate = wxMpTemplateMsgMaker.makeMsg(msgData);
            wxMessageTemplate.setToUser(openId);
//            wxMpService.getTemplateMsgService().sendTemplateMsg(wxMessageTemplate);

            String uri = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + wxMpService.getAccessToken();
//            YunpianClient yunpianClient = new YunpianClient().init();
//            Future<HttpResponse> future = yunpianClient.post(uri, wxMessageTemplate.toJson());
//            HttpResponse response1 = future.get();
//            System.err.println(response1);

            final CountDownLatch latch1 = new CountDownLatch(1);
            final HttpPost request2 = new HttpPost(uri);
            request2.setEntity(new StringEntity(wxMessageTemplate.toJson()));
            CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
            httpclient.start();
            httpclient.execute(request2, new FutureCallback<HttpResponse>() {

                @Override
                public void completed(final HttpResponse response2) {
                    latch1.countDown();
                    System.err.println(request2.getRequestLine() + "->" + response2.getStatusLine());
                }

                @Override
                public void failed(final Exception ex) {
                    latch1.countDown();
                    System.err.println(request2.getRequestLine() + "->" + ex);
                }

                @Override
                public void cancelled() {
                    latch1.countDown();
                    System.err.println(request2.getRequestLine() + " cancelled");
                }

            });
            latch1.await();
        } catch (Exception e) {
            sendResult.setSuccess(false);
            sendResult.setInfo(e.toString());
            return sendResult;
        }

        sendResult.setSuccess(true);
        return sendResult;
    }

    /**
     * 微信公众号配置
     *
     * @return WxMpConfigStorage
     */
    private static WxMpInMemoryConfigStorage wxMpConfigStorage() {
        WxMpInMemoryConfigStorage configStorage = new WxMpInMemoryConfigStorage();
        configStorage.setAppId(App.config.getWechatAppId());
        configStorage.setSecret(App.config.getWechatAppSecret());
        configStorage.setToken(App.config.getWechatToken());
        configStorage.setAesKey(App.config.getWechatAesKey());
        if (App.config.isMpUseProxy()) {
            configStorage.setHttpProxyHost(App.config.getMpProxyHost());
            configStorage.setHttpProxyPort(Integer.parseInt(App.config.getMpProxyPort()));
            configStorage.setHttpProxyUsername(App.config.getMpProxyUserName());
            configStorage.setHttpProxyPassword(App.config.getMpProxyPassword());
        }
        DefaultApacheHttpClientBuilder clientBuilder = DefaultApacheHttpClientBuilder.get();
        //从连接池获取链接的超时时间(单位ms)
        clientBuilder.setConnectionRequestTimeout(10000);
        //建立链接的超时时间(单位ms)
        clientBuilder.setConnectionTimeout(5000);
        //连接池socket超时时间(单位ms)
        clientBuilder.setSoTimeout(5000);
        //空闲链接的超时时间(单位ms)
        clientBuilder.setIdleConnTimeout(60000);
        //空闲链接的检测周期(单位ms)
        clientBuilder.setCheckWaitTime(60000);
        //每路最大连接数
        clientBuilder.setMaxConnPerHost(App.config.getMaxThreadPool());
        //连接池最大连接数
        clientBuilder.setMaxTotalConn(App.config.getMaxThreadPool() * 2);
        //HttpClient请求时使用的User Agent
//        clientBuilder.setUserAgent(..)
        configStorage.setApacheHttpClientBuilder(clientBuilder);
        return configStorage;
    }

    /**
     * 获取微信公众号工具服务
     *
     * @return WxMpService
     */
    public static WxMpService getWxMpService() {
        if (wxMpConfigStorage == null) {
            synchronized (PushControl.class) {
                if (wxMpConfigStorage == null) {
                    wxMpConfigStorage = wxMpConfigStorage();
                }
            }
        }
        if (wxMpService == null && wxMpConfigStorage != null) {
            synchronized (PushControl.class) {
                if (wxMpService == null && wxMpConfigStorage != null) {
                    wxMpService = new WxMpServiceImpl();
                    wxMpService.setWxMpConfigStorage(wxMpConfigStorage);
                }
            }
        }
        return wxMpService;
    }
}
