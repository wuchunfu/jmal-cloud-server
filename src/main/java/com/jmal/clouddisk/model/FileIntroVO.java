package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author jmal
 * @Description 文件简介
 * @Date 2020/11/12 1:54 下午
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileIntroVO extends FileBase {
    private String userId;
    private String username;
    /***
     * 文件路径(根路径为"/")
     */
    private String path;
    /***
     * updateDate 距离现在的时间
     */
    private Long agoTime;
    /***
     * 显示大小
     */
    private String showSize;
    /***
     * 文件后缀名
     */
    private String suffix;
    /***
     * 是否收藏
     */
    private Boolean isFavorite;
    /***
     * 是否分享
     */
    private Boolean isShare;
    /**
     * oss目录名称
     */
    private String ossFolder;
    /**
     * oss平台类型
     */
    private String ossPlatform;
    /***
     * 是否为私密链接
     */
    private Boolean isPrivacy;

    private String contentText;

    private Boolean shareBase;
    /***
     * 分享有效期
     */
    private Long expiresAt;
    private Music music;
    /**
     * 媒体封面
     */
    private String mediaCover;
    /**
     * m3u8文件路径(相对路径)
     */
    private String m3u8;
    /***
     * 图片的宽度
     */
    private String w;
    /***
     * 图片的高度
     */
    private String h;
    /***
     * 封面
     */
    private String cover;
    /**
     * 挂载的文件id
     */
    private String mountFileId;
    private List<Tag> tags;
    /**
     * 操作权限
     */
    private List<OperationPermission> operationPermissionList;
}
