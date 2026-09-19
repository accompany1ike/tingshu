package com.atguigu.tingshu.common.handler;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 全局异常处理类
 *
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result error(Exception e){
        e.printStackTrace();
        return Result.fail();
    }

    /**
     * 自定义异常处理方法
     * @param e
     * @return
     */
    @ExceptionHandler(GuiguException.class)
    @ResponseBody
    public Result error(GuiguException e){
        return Result.build(null,e.getCode(), e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseBody
    public Result llegalArgumentException(Exception e) {
        log.error("触发异常拦截: " + e.getMessage(), e);
        return Result.build(null, ResultCodeEnum.ARGUMENT_VALID_ERROR);
    }

    /**
     * 上传文件超过大小限制（由 servlet 容器在解析 multipart 时抛出，控制器还来不及处理）
     * @param e
     * @return
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public Result maxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.error("上传文件超过大小限制: {}", e.getMessage());
        return Result.build(null, ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "上传文件过大，请压缩后重试");
    }

    /**
     * 缺少上传文件（multipart 里没有对应的表单字段，例如上传时字段名不是 file）
     * 这类异常在参数解析阶段抛出，走不到业务代码，需要单独给出可读提示
     *
     * @param e
     * @return
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseBody
    public Result missingServletRequestPart(MissingServletRequestPartException e) {
        log.error("上传文件缺失，表单字段名: {}", e.getRequestPartName());
        return Result.build(null, ResultCodeEnum.ILLEGAL_REQUEST.getCode(),
                "请选择要上传的文件（表单字段名应为 " + e.getRequestPartName() + "）");
    }

    /**
     * 缺少必要的请求参数
     *
     * @param e
     * @return
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseBody
    public Result missingServletRequestParameter(MissingServletRequestParameterException e) {
        log.error("缺少必要参数: {}", e.getParameterName());
        return Result.build(null, ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "缺少必要参数：" + e.getParameterName());
    }

    /**
     * 请求体格式不正确（例如提交的JSON不合法）
     *
     * @param e
     * @return
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public Result httpMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("请求体解析失败: {}", e.getMessage());
        return Result.build(null, ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "请求体格式不正确，请检查提交的内容");
    }

    /**
     * 不是合法的文件上传请求（例如上传接口被用普通 POST 调用，没有 multipart/form-data）
     * 注意：MaxUploadSizeExceededException 是它的子类，会优先命中上面更具体的处理方法
     *
     * @param e
     * @return
     */
    @ExceptionHandler(MultipartException.class)
    @ResponseBody
    public Result multipartException(MultipartException e) {
        log.error("文件上传请求解析失败: {}", e.getMessage());
        return Result.build(null, ResultCodeEnum.ILLEGAL_REQUEST.getCode(),
                "请使用 multipart/form-data 方式上传文件（表单字段名应为 file）");
    }


    @ExceptionHandler(value = BindException.class)
    @ResponseBody
    public Result error(BindException exception) {
        BindingResult result = exception.getBindingResult();
        Map<String, Object> errorMap = new HashMap<>();
        List<FieldError> fieldErrors = result.getFieldErrors();
        fieldErrors.forEach(error -> {
            log.error("field: " + error.getField() + ", msg:" + error.getDefaultMessage());
            errorMap.put(error.getField(), error.getDefaultMessage());
        });
        return Result.build(errorMap, ResultCodeEnum.ARGUMENT_VALID_ERROR);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    @ResponseBody
    public Result error(MethodArgumentNotValidException exception) {
        BindingResult result = exception.getBindingResult();
        Map<String, Object> errorMap = new HashMap<>();
        List<FieldError> fieldErrors = result.getFieldErrors();
        fieldErrors.forEach(error -> {
            log.error("field: " + error.getField() + ", msg:" + error.getDefaultMessage());
            errorMap.put(error.getField(), error.getDefaultMessage());
        });
        return Result.build(errorMap, ResultCodeEnum.ARGUMENT_VALID_ERROR);
    }


}
