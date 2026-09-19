package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.FileUploadService;
import com.atguigu.tingshu.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "上传管理接口")
@RestController
@RequestMapping("api/album")
public class FileUploadApiController {

    @Autowired
    private FileUploadService fileUploadService;

    /**
     * 上传图片（专辑封面、声音封面等）
     * <p>
     * 前端小程序使用 wx.uploadFile，表单字段名固定为 file；
     * 返回结果中的 data 即图片访问地址，可直接作为 coverUrl 提交保存
     * <p>
     * 说明：本接口不强制登录。它是"添加专辑"流程中的一个辅助步骤（先传图再提交表单），
     * 真正的业务动作 saveAlbumInfo 仍然是登录后才能调用，所以匿名上传最多只是往对象存储
     * 里多写一个小文件，不会产生业务数据；而一旦登录状态异常（token 过期、登录服务未就绪），
     * 强制登录会让整个添加专辑流程直接卡在第一步，前端还拿不到明确提示。
     * 如需收紧：给本方法加回 @GuiGuLogin 即可（小程序端已经会在上传请求头里带 token）。
     *
     * @param file 图片文件
     * @return 图片访问地址
     */
    @Operation(summary = "上传图片")
    @PostMapping("/fileUpload")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileUploadService.uploadImage(file));
    }

}
