package com.fangxuele.tool.push.ui.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.fangxuele.tool.push.App;
import com.fangxuele.tool.push.dao.TWxMpUserMapper;
import com.fangxuele.tool.push.domain.TWxMpUser;
import com.fangxuele.tool.push.logic.MessageTypeEnum;
import com.fangxuele.tool.push.logic.PushData;
import com.fangxuele.tool.push.logic.msgsender.WxMpTemplateMsgSender;
import com.fangxuele.tool.push.ui.component.TableInCellImageLabelRenderer;
import com.fangxuele.tool.push.ui.form.MemberForm;
import com.fangxuele.tool.push.util.ConsoleUtil;
import com.fangxuele.tool.push.util.DbUtilMySQL;
import com.fangxuele.tool.push.util.FileCharSetUtil;
import com.fangxuele.tool.push.util.JTableUtil;
import com.fangxuele.tool.push.util.MybatisUtil;
import com.opencsv.CSVReader;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import me.chanjar.weixin.mp.bean.result.WxMpUserList;
import me.chanjar.weixin.mp.bean.tag.WxTagListUser;
import me.chanjar.weixin.mp.bean.tag.WxUserTag;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <pre>
 * 准备目标数据tab相关事件监听
 * </pre>
 *
 * @author <a href="https://github.com/rememberber">RememBerBer</a>
 * @since 2017/6/19.
 */
public class MemberListener {
    private static final Log logger = LogFactory.get();

    private static Map<String, Long> userTagMap = new HashMap<>();

    /**
     * 用于导入多个标签的用户时去重判断
     */
    public static Set<String> tagUserSet;

    public static final String TXT_FILE_DATA_SEPERATOR_REGEX = "\\|";

    private static List<String> toSearchRowsList;

    private static TWxMpUserMapper tWxMpUserMapper = MybatisUtil.getSqlSession().getMapper(TWxMpUserMapper.class);

    private static JProgressBar progressBar = MemberForm.memberForm.getMemberTabImportProgressBar();
    private static JTextField filePathField = MemberForm.memberForm.getMemberFilePathField();
    private static JLabel memberCountLabel = MemberForm.memberForm.getMemberTabCountLabel();
    private static JPanel memberPanel = MemberForm.memberForm.getMemberPanel();
    private static JTable memberListTable = MemberForm.memberForm.getMemberListTable();

    public static void addListeners() {
        // 从文件导入按钮事件
        MemberForm.memberForm.getImportFromFileButton().addActionListener(e -> ThreadUtil.execute(() -> {
            if (StringUtils.isBlank(filePathField.getText())) {
                JOptionPane.showMessageDialog(memberPanel, "请填写或点击浏览按钮选择要导入的文件的路径！", "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            File file = new File(filePathField.getText());
            if (!file.exists()) {
                JOptionPane.showMessageDialog(memberPanel, filePathField.getText() + "\n该文件不存在！", "文件不存在",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            CSVReader reader = null;
            FileReader fileReader;

            int currentImported = 0;

            try {
                progressBar.setVisible(true);
                progressBar.setIndeterminate(true);
                String fileNameLowerCase = file.getName().toLowerCase();

                if (fileNameLowerCase.endsWith(".csv")) {
                    // 可以解决中文乱码问题
                    DataInputStream in = new DataInputStream(new FileInputStream(file));
                    reader = new CSVReader(new InputStreamReader(in, FileCharSetUtil.getCharSet(file)));
                    String[] nextLine;
                    PushData.allUser = Collections.synchronizedList(new ArrayList<>());

                    while ((nextLine = reader.readNext()) != null) {
                        PushData.allUser.add(nextLine);
                        currentImported++;
                        memberCountLabel.setText(String.valueOf(currentImported));
                    }
                } else if (fileNameLowerCase.endsWith(".xlsx") || fileNameLowerCase.endsWith(".xls")) {
                    ExcelReader excelReader = ExcelUtil.getReader(file);
                    List<List<Object>> readAll = excelReader.read(1, Integer.MAX_VALUE);
                    PushData.allUser = Collections.synchronizedList(new ArrayList<>());

                    for (List<Object> objects : readAll) {
                        if (objects != null && objects.size() > 0) {
                            String[] nextLine = new String[objects.size()];
                            for (int i = 0; i < objects.size(); i++) {
                                nextLine[i] = objects.get(i).toString();
                            }
                            PushData.allUser.add(nextLine);
                            currentImported++;
                            memberCountLabel.setText(String.valueOf(currentImported));
                        }
                    }
                } else if (fileNameLowerCase.endsWith(".txt")) {
                    fileReader = new FileReader(file, FileCharSetUtil.getCharSetName(file));
                    PushData.allUser = Collections.synchronizedList(new ArrayList<>());
                    BufferedReader br = fileReader.getReader();
                    String line;
                    while ((line = br.readLine()) != null) {
                        PushData.allUser.add(line.split(TXT_FILE_DATA_SEPERATOR_REGEX));
                        currentImported++;
                        memberCountLabel.setText(String.valueOf(currentImported));
                    }
                } else {
                    JOptionPane.showMessageDialog(memberPanel, "不支持该格式的文件！", "文件格式不支持",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                renderMemberListTable();
                JOptionPane.showMessageDialog(memberPanel, "导入完成！", "完成",
                        JOptionPane.INFORMATION_MESSAGE);

                App.config.setMemberFilePath(filePathField.getText());
                App.config.save();
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(memberPanel, "导入失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
                e1.printStackTrace();
            } finally {
                progressBar.setMaximum(100);
                progressBar.setValue(100);
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e1) {
                        logger.error(e1);
                        e1.printStackTrace();
                    }
                }
            }
        }));

        // 导入全员按钮事件
        MemberForm.memberForm.getMemberImportAllButton().addActionListener(e -> ThreadUtil.execute(() -> {
            try {
                getMpUserList();
                renderMemberListTable();
                JOptionPane.showMessageDialog(memberPanel, "导入完成！", "完成",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (WxErrorException e1) {
                JOptionPane.showMessageDialog(memberPanel, "导入失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
                e1.printStackTrace();
            } finally {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
            }
        }));

        // 刷新可选的标签按钮事件
        MemberForm.memberForm.getMemberImportTagFreshButton().addActionListener(e -> {
            WxMpService wxMpService = WxMpTemplateMsgSender.getWxMpService();
            if (wxMpService.getWxMpConfigStorage() == null) {
                return;
            }

            try {
                List<WxUserTag> wxUserTagList = wxMpService.getUserTagService().tagGet();

                MemberForm.memberForm.getMemberImportTagComboBox().removeAllItems();
                userTagMap = new HashMap<>();

                for (WxUserTag wxUserTag : wxUserTagList) {
                    String item = wxUserTag.getName() + "/" + wxUserTag.getCount() + "用户";
                    MemberForm.memberForm.getMemberImportTagComboBox().addItem(item);
                    userTagMap.put(item, wxUserTag.getId());
                }

            } catch (WxErrorException e1) {
                JOptionPane.showMessageDialog(memberPanel, "刷新失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
                e1.printStackTrace();
            }
        });

        // 导入选择的标签分组用户按钮事件(取并集)
        MemberForm.memberForm.getMemberImportTagButton().addActionListener(e -> ThreadUtil.execute(() -> {
            try {
                if (MemberForm.memberForm.getMemberImportTagComboBox().getSelectedItem() != null
                        && StringUtils.isNotEmpty(MemberForm.memberForm.getMemberImportTagComboBox().getSelectedItem().toString())) {

                    long selectedTagId = userTagMap.get(MemberForm.memberForm.getMemberImportTagComboBox().getSelectedItem());
                    getMpUserListByTag(selectedTagId, false);
                    renderMemberListTable();
                    JOptionPane.showMessageDialog(memberPanel, "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(memberPanel, "请先选择需要导入的标签！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (WxErrorException e1) {
                JOptionPane.showMessageDialog(memberPanel, "导入失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
                e1.printStackTrace();
            } finally {
                progressBar.setIndeterminate(false);
                progressBar.setValue(progressBar.getMaximum());
                progressBar.setVisible(false);
            }
        }));

        // 导入选择的标签分组用户按钮事件(取交集)
        MemberForm.memberForm.getMemberImportTagRetainButton().addActionListener(e -> ThreadUtil.execute(() -> {
            try {
                if (MemberForm.memberForm.getMemberImportTagComboBox().getSelectedItem() != null
                        && StringUtils.isNotEmpty(MemberForm.memberForm.getMemberImportTagComboBox().getSelectedItem().toString())) {

                    long selectedTagId = userTagMap.get(MemberForm.memberForm.getMemberImportTagComboBox().getSelectedItem());
                    getMpUserListByTag(selectedTagId, true);
                    renderMemberListTable();
                    JOptionPane.showMessageDialog(memberPanel, "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(memberPanel, "请先选择需要导入的标签！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (WxErrorException e1) {
                JOptionPane.showMessageDialog(memberPanel, "导入失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
                e1.printStackTrace();
            } finally {
                progressBar.setIndeterminate(false);
                progressBar.setValue(progressBar.getMaximum());
                progressBar.setVisible(false);
            }
        }));

        // 清除按钮事件
        MemberForm.memberForm.getClearImportButton().addActionListener(e -> {
            int isClear = JOptionPane.showConfirmDialog(memberPanel, "确认清除？", "确认",
                    JOptionPane.YES_NO_OPTION);
            if (isClear == JOptionPane.YES_OPTION) {
                MemberForm.clearMember();
            }
        });

        // 从sql导入 按钮事件
        MemberForm.memberForm.getImportFromSqlButton().addActionListener(e -> ThreadUtil.execute(() -> {
            if (StringUtils.isBlank(App.config.getMysqlUrl()) || StringUtils.isBlank(App.config.getMysqlUser())) {
                JOptionPane.showMessageDialog(memberPanel, "请先在设置中填写并保存MySQL的配置信息！", "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String querySql = MemberForm.memberForm.getImportFromSqlTextArea().getText();
            if (StringUtils.isBlank(querySql)) {
                JOptionPane.showMessageDialog(memberPanel, "请先填写要执行导入的SQL！", "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            if (StringUtils.isNotEmpty(querySql)) {
                try {
                    MemberForm.memberForm.getImportFromSqlButton().setEnabled(false);
                    MemberForm.memberForm.getImportFromSqlButton().updateUI();
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);

                    // 获取SQLServer连接实例
                    DbUtilMySQL dbUtilMySQL = DbUtilMySQL.getInstance();

                    // 表查询
                    ResultSet resultSet = dbUtilMySQL.executeQuery(querySql);
                    PushData.allUser = Collections.synchronizedList(new ArrayList<>());
                    int currentImported = 0;

                    int columnCount = resultSet.getMetaData().getColumnCount();
                    while (resultSet.next()) {
                        String[] msgData = new String[columnCount];
                        for (int i = 0; i < columnCount; i++) {
                            msgData[i] = resultSet.getString(i).trim();
                        }
                        PushData.allUser.add(msgData);
                        currentImported++;
                        memberCountLabel.setText(String.valueOf(currentImported));
                    }
                    renderMemberListTable();
                    JOptionPane.showMessageDialog(memberPanel, "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);

                    App.config.setMemberSql(querySql);
                    App.config.save();
                } catch (Exception e1) {
                    JOptionPane.showMessageDialog(memberPanel, "导入失败！\n\n" + e1.getMessage(), "失败",
                            JOptionPane.ERROR_MESSAGE);
                    logger.error(e1);
                    e1.printStackTrace();
                } finally {
                    MemberForm.memberForm.getImportFromSqlButton().setEnabled(true);
                    MemberForm.memberForm.getImportFromSqlButton().updateUI();
                    progressBar.setMaximum(100);
                    progressBar.setValue(100);
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                }
            }
        }));

        // 浏览按钮
        MemberForm.memberForm.getMemberImportExploreButton().addActionListener(e -> {
            File beforeFile = new File(filePathField.getText());
            JFileChooser fileChooser;

            if (beforeFile.exists()) {
                fileChooser = new JFileChooser(beforeFile);
            } else {
                fileChooser = new JFileChooser();
            }

            FileFilter filter = new FileNameExtensionFilter("*.txt,*.csv,*.xlsx,*.xls", "txt", "csv", "TXT", "CSV", "xlsx", "xls");
            fileChooser.setFileFilter(filter);

            int approve = fileChooser.showOpenDialog(memberPanel);
            if (approve == JFileChooser.APPROVE_OPTION) {
                filePathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }

        });

        // 全选按钮事件
        MemberForm.memberForm.getSelectAllButton().addActionListener(e -> ThreadUtil.execute(() -> memberListTable.selectAll()));

        // 删除按钮事件
        MemberForm.memberForm.getDeleteButton().addActionListener(e -> ThreadUtil.execute(() -> {
            try {
                int[] selectedRows = memberListTable.getSelectedRows();
                if (selectedRows.length == 0) {
                    JOptionPane.showMessageDialog(memberPanel, "请至少选择一个！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    int isDelete = JOptionPane.showConfirmDialog(memberPanel, "确认删除？", "确认",
                            JOptionPane.YES_NO_OPTION);
                    if (isDelete == JOptionPane.YES_OPTION) {
                        DefaultTableModel tableModel = (DefaultTableModel) memberListTable.getModel();
                        for (int i = selectedRows.length; i > 0; i--) {
                            tableModel.removeRow(memberListTable.getSelectedRow());
                        }
                        memberListTable.updateUI();
                    }
                }
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(memberPanel, "删除失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
            }
        }));

        // 导入按钮事件
        MemberForm.memberForm.getImportSelectedButton().addActionListener(e -> ThreadUtil.execute(() -> {
            try {
                int[] selectedRows = memberListTable.getSelectedRows();
                if (selectedRows.length <= 0) {
                    JOptionPane.showMessageDialog(memberPanel, "请至少选择一个！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    PushData.allUser = Collections.synchronizedList(new ArrayList<>());
                    progressBar.setIndeterminate(true);
                    progressBar.setVisible(true);
                    for (int selectedRow : selectedRows) {
                        String toImportData = (String) memberListTable.getValueAt(selectedRow, 0);
                        PushData.allUser.add(toImportData.split(TXT_FILE_DATA_SEPERATOR_REGEX));
                        memberCountLabel.setText(String.valueOf(PushData.allUser.size()));
                        progressBar.setMaximum(100);
                        progressBar.setValue(100);
                        progressBar.setIndeterminate(false);
                    }
                    JOptionPane.showMessageDialog(memberPanel, "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(memberPanel, "导入失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
            } finally {
                progressBar.setMaximum(100);
                progressBar.setValue(100);
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
            }
        }));

        // 导出按钮事件
        MemberForm.memberForm.getExportButton().addActionListener(e -> ThreadUtil.execute(() -> {
            int[] selectedRows = memberListTable.getSelectedRows();
            int columnCount = memberListTable.getColumnCount();
            ExcelWriter writer = null;
            try {
                if (selectedRows.length > 0) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    int approve = fileChooser.showOpenDialog(memberPanel);
                    String exportPath;
                    if (approve == JFileChooser.APPROVE_OPTION) {
                        exportPath = fileChooser.getSelectedFile().getAbsolutePath();
                    } else {
                        return;
                    }

                    List<String> rowData;
                    List<List<String>> rows = Lists.newArrayList();
                    for (int selectedRow : selectedRows) {
                        rowData = Lists.newArrayList();
                        for (int i = 0; i < columnCount; i++) {
                            String data = (String) memberListTable.getValueAt(selectedRow, i);
                            rowData.add(data);
                        }
                        rows.add(rowData);
                    }

                    String nowTime = DateUtil.now().replace(":", "_").replace(" ", "_");
                    String fileName = "MemberExport_" + MessageTypeEnum.getName(App.config.getMsgType()) + "_" + nowTime + ".xlsx";
                    //通过工具类创建writer
                    writer = ExcelUtil.getWriter(exportPath + File.separator + fileName);

                    //合并单元格后的标题行，使用默认标题样式
                    writer.merge(rows.get(0).size() - 1, "目标用户列表导出");
                    //一次性写出内容，强制输出标题
                    writer.write(rows);

                    writer.flush();
                    JOptionPane.showMessageDialog(memberPanel, "导出成功！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    try {
                        Desktop desktop = Desktop.getDesktop();
                        desktop.open(FileUtil.file(exportPath + File.separator + fileName));
                    } catch (Exception e2) {
                        logger.error(e2);
                    }
                } else {
                    JOptionPane.showMessageDialog(memberPanel, "请至少选择一个！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(memberPanel, "导出失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
            } finally {
                //关闭writer，释放内存
                if (writer != null) {
                    writer.close();
                }
            }
        }));

        // 搜索按钮事件
        MemberForm.memberForm.getSearchButton().addActionListener(e -> searchEvent());

        // 线程池数键入回车
        MemberForm.memberForm.getSearchTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                try {
                    searchEvent();
                } catch (Exception e1) {
                    logger.error(e1);
                } finally {
                    super.keyPressed(e);
                }
            }
        });

        // 清空本地缓存按钮事件
        MemberForm.memberForm.getClearDbCacheButton().addActionListener(e -> {
            int count = tWxMpUserMapper.deleteAll();
            JOptionPane.showMessageDialog(memberPanel, "清理完毕！\n\n共清理：" + count + "条本地数据", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private static void searchEvent() {
        ThreadUtil.execute(() -> {
            int rowCount = memberListTable.getRowCount();
            int columnCount = memberListTable.getColumnCount();
            try {
                if (rowCount > 0 || toSearchRowsList != null) {
                    if (toSearchRowsList == null) {
                        toSearchRowsList = Lists.newArrayList();
                        List<String> rowData;
                        for (int i = 0; i < rowCount; i++) {
                            rowData = Lists.newArrayList();
                            for (int j = 0; j < columnCount; j++) {
                                String data = (String) memberListTable.getValueAt(i, j);
                                rowData.add(data);
                            }
                            toSearchRowsList.add(String.join("==", rowData));
                        }
                    }

                    String keyWord = MemberForm.memberForm.getSearchTextField().getText();
                    List<String> searchResultList = toSearchRowsList.parallelStream().filter(rowStr -> rowStr.contains(keyWord)).collect(Collectors.toList());

                    DefaultTableModel tableModel = (DefaultTableModel) memberListTable.getModel();
                    tableModel.setRowCount(0);
                    for (String rowString : searchResultList) {
                        tableModel.addRow(rowString.split("=="));
                    }
                }
            } catch (Exception e1) {
                logger.error(e1);
            }
        });
    }

    /**
     * 拉取公众平台用户列表
     */
    public static void getMpUserList() throws WxErrorException {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        WxMpService wxMpService = WxMpTemplateMsgSender.getWxMpService();
        if (wxMpService.getWxMpConfigStorage() == null) {
            return;
        }

        WxMpUserList wxMpUserList = wxMpService.getUserService().userList(null);

        ConsoleUtil.consoleWithLog("关注该公众账号的总用户数：" + wxMpUserList.getTotal());
        ConsoleUtil.consoleWithLog("拉取的OPENID个数：" + wxMpUserList.getCount());

        progressBar.setIndeterminate(false);
        progressBar.setMaximum((int) wxMpUserList.getTotal());
        int importedCount = 0;
        PushData.allUser = Collections.synchronizedList(new ArrayList<>());

        if (wxMpUserList.getCount() == 0) {
            memberCountLabel.setText(String.valueOf(importedCount));
            progressBar.setValue(importedCount);
            return;
        }

        List<String> openIds = wxMpUserList.getOpenids();

        for (String openId : openIds) {
            PushData.allUser.add(new String[]{openId});
            importedCount++;
            memberCountLabel.setText(String.valueOf(importedCount));
            progressBar.setValue(importedCount);
        }

        while (StringUtils.isNotEmpty(wxMpUserList.getNextOpenid())) {
            wxMpUserList = wxMpService.getUserService().userList(wxMpUserList.getNextOpenid());

            ConsoleUtil.consoleWithLog("拉取的OPENID个数：" + wxMpUserList.getCount());

            if (wxMpUserList.getCount() == 0) {
                break;
            }
            openIds = wxMpUserList.getOpenids();
            for (String openId : openIds) {
                PushData.allUser.add(new String[]{openId});
                importedCount++;
                memberCountLabel.setText(String.valueOf(importedCount));
                progressBar.setValue(importedCount);
            }
        }

        progressBar.setValue((int) wxMpUserList.getTotal());
    }

    /**
     * 按标签拉取公众平台用户列表
     *
     * @param tagId
     * @param retain 是否取交集
     * @throws WxErrorException
     */
    public static void getMpUserListByTag(Long tagId, boolean retain) throws WxErrorException {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        WxMpService wxMpService = WxMpTemplateMsgSender.getWxMpService();
        if (wxMpService.getWxMpConfigStorage() == null) {
            return;
        }

        WxTagListUser wxTagListUser = wxMpService.getUserTagService().tagListUser(tagId, "");

        ConsoleUtil.consoleWithLog("拉取的OPENID个数：" + wxTagListUser.getCount());

        if (wxTagListUser.getCount() == 0) {
            return;
        }

        List<String> openIds = wxTagListUser.getData().getOpenidList();

        if (tagUserSet == null) {
            tagUserSet = Collections.synchronizedSet(new HashSet<>());
            tagUserSet.addAll(openIds);
        }

        if (retain) {
            // 取交集
            tagUserSet.retainAll(openIds);
        } else {
            // 无重复并集
            openIds.removeAll(tagUserSet);
            tagUserSet.addAll(openIds);
        }


        while (StringUtils.isNotEmpty(wxTagListUser.getNextOpenid())) {
            wxTagListUser = wxMpService.getUserTagService().tagListUser(tagId, wxTagListUser.getNextOpenid());

            ConsoleUtil.consoleWithLog("拉取的OPENID个数：" + wxTagListUser.getCount());

            if (wxTagListUser.getCount() == 0) {
                break;
            }
            openIds = wxTagListUser.getData().getOpenidList();

            if (retain) {
                // 取交集
                tagUserSet.retainAll(openIds);
            } else {
                // 无重复并集
                openIds.removeAll(tagUserSet);
                tagUserSet.addAll(openIds);
            }
        }

        PushData.allUser = Collections.synchronizedList(new ArrayList<>());
        for (String openId : tagUserSet) {
            PushData.allUser.add(new String[]{openId});
        }

        memberCountLabel.setText(String.valueOf(PushData.allUser.size()));
        progressBar.setIndeterminate(false);
        progressBar.setValue(progressBar.getMaximum());

    }

    /**
     * 获取导入数据信息列表
     */
    private static void renderMemberListTable() {
        toSearchRowsList = null;
        DefaultTableModel tableModel = (DefaultTableModel) memberListTable.getModel();
        tableModel.setRowCount(0);
        progressBar.setVisible(true);
        progressBar.setMaximum(PushData.allUser.size());

        int msgType = App.config.getMsgType();

        // 是否是微信平台类消息
        boolean isWeixinTypeMsg = false;
        if (msgType == MessageTypeEnum.MP_TEMPLATE_CODE || msgType == MessageTypeEnum.MA_TEMPLATE_CODE
                || msgType == MessageTypeEnum.KEFU_CODE || msgType == MessageTypeEnum.KEFU_PRIORITY_CODE) {
            isWeixinTypeMsg = true;
        }
        // 导入列表
        List<String> headerNameList = Lists.newArrayList();
        headerNameList.add("Data");
        if (isWeixinTypeMsg) {
            if (MemberForm.memberForm.getImportOptionAvatarCheckBox().isSelected()) {
                headerNameList.add("头像");
            }
            if (MemberForm.memberForm.getImportOptionBasicInfoCheckBox().isSelected()) {
                headerNameList.add("昵称");
                headerNameList.add("性别");
                headerNameList.add("地区");
                headerNameList.add("关注时间");
            }
            headerNameList.add("openId");
        } else {
            headerNameList.add("数据");
        }

        String[] headerNames = new String[headerNameList.size()];
        headerNameList.toArray(headerNames);
        DefaultTableModel model = new DefaultTableModel(null, headerNames);
        memberListTable.setModel(model);
        if (isWeixinTypeMsg && MemberForm.memberForm.getImportOptionAvatarCheckBox().isSelected()) {
            memberListTable.getColumn("头像").setCellRenderer(new TableInCellImageLabelRenderer());
        }

        DefaultTableCellRenderer hr = (DefaultTableCellRenderer) memberListTable.getTableHeader()
                .getDefaultRenderer();
        // 表头列名居左
        hr.setHorizontalAlignment(DefaultTableCellRenderer.LEFT);

        // 隐藏第0列Data数据列
        JTableUtil.hideColumn(memberListTable, 0);

        // 设置行高
        if (isWeixinTypeMsg && MemberForm.memberForm.getImportOptionAvatarCheckBox().isSelected()) {
            memberListTable.setRowHeight(66);
        } else {
            memberListTable.setRowHeight(36);
        }

        List<Object> rowDataList;
        WxMpService wxMpService = null;
        boolean needToGetInfoFromWeiXin = false;
        if (isWeixinTypeMsg && (MemberForm.memberForm.getImportOptionBasicInfoCheckBox().isSelected() ||
                MemberForm.memberForm.getImportOptionAvatarCheckBox().isSelected())) {
            needToGetInfoFromWeiXin = true;
        }
        if (needToGetInfoFromWeiXin) {
            wxMpService = WxMpTemplateMsgSender.getWxMpService();
        }
        for (int i = 0; i < PushData.allUser.size(); i++) {
            String[] importedData = PushData.allUser.get(i);
            try {
                String openId = importedData[0];
                rowDataList = new ArrayList<>();
                rowDataList.add(String.join("|", importedData));
                if (needToGetInfoFromWeiXin) {
                    WxMpUser wxMpUser = null;
                    TWxMpUser tWxMpUser = tWxMpUserMapper.selectByPrimaryKey(openId);
                    if (tWxMpUser != null) {
                        wxMpUser = new WxMpUser();
                        BeanUtil.copyProperties(tWxMpUser, wxMpUser);
                    } else {
                        if (wxMpService != null) {
                            try {
                                wxMpUser = wxMpService.getUserService().userInfo(openId);
                                if (wxMpUser != null) {
                                    tWxMpUser = new TWxMpUser();
                                    BeanUtil.copyProperties(wxMpUser, tWxMpUser);
                                    tWxMpUserMapper.insertSelective(tWxMpUser);
                                }
                            } catch (Exception e) {
                                logger.error(e);
                            }
                        }
                    }

                    if (wxMpUser != null) {
                        if (MemberForm.memberForm.getImportOptionAvatarCheckBox().isSelected()) {
                            rowDataList.add(wxMpUser.getHeadImgUrl());
                        }
                        if (MemberForm.memberForm.getImportOptionBasicInfoCheckBox().isSelected()) {
                            rowDataList.add(wxMpUser.getNickname());
                            rowDataList.add(wxMpUser.getSexDesc());
                            rowDataList.add(wxMpUser.getCountry() + "-" + wxMpUser.getProvince() + "-" + wxMpUser.getCity());
                            rowDataList.add(DateFormatUtils.format(wxMpUser.getSubscribeTime() * 1000, "yyyy-MM-dd HH:mm:ss"));
                        }
                    } else {
                        if (MemberForm.memberForm.getImportOptionAvatarCheckBox().isSelected()) {
                            rowDataList.add("");
                        }
                        if (MemberForm.memberForm.getImportOptionBasicInfoCheckBox().isSelected()) {
                            rowDataList.add("");
                            rowDataList.add("");
                            rowDataList.add("");
                            rowDataList.add("");
                        }
                    }
                    rowDataList.add(openId);
                } else {
                    rowDataList.add(String.join("|", importedData));
                }

                model.addRow(rowDataList.toArray());
            } catch (Exception e) {
                logger.error(e);
            }
            progressBar.setValue(i + 1);
        }
    }
}
