package com.geekerstar.controller;

import java.util.List;

import com.geekerstar.utils.GeekJSONResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.geekerstar.enums.OperatorFriendRequestTypeEnum;
import com.geekerstar.enums.SearchFriendsStatusEnum;
import com.geekerstar.pojo.Users;
import com.geekerstar.pojo.bo.UsersBO;
import com.geekerstar.pojo.vo.MyFriendsVO;
import com.geekerstar.pojo.vo.UsersVO;
import com.geekerstar.service.UserService;
import com.geekerstar.utils.FastDFSClient;
import com.geekerstar.utils.FileUtils;
import com.geekerstar.utils.MD5Utils;

@RestController
@RequestMapping("u")
public class UserController {

	@Autowired
	private UserService userService;

	@Autowired
	private FastDFSClient fastDFSClient;

	/**
	 * 用户注册/登录
	 * @param user
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/registOrLogin")
	public GeekJSONResult registOrLogin(@RequestBody Users user) throws Exception {

		// 0. 判断用户名和密码不能为空
		if (StringUtils.isBlank(user.getUsername())
				|| StringUtils.isBlank(user.getPassword())) {
			return GeekJSONResult.errorMsg("用户名或密码不能为空...");
		}

		// 1. 判断用户名是否存在，如果存在就登录，如果不存在则注册
		boolean usernameIsExist = userService.queryUsernameIsExist(user.getUsername());
		Users userResult = null;
		if (usernameIsExist) {
			// 1.1 登录
			userResult = userService.queryUserForLogin(user.getUsername(),
									MD5Utils.getMD5Str(user.getPassword()));
			if (userResult == null) {
				return GeekJSONResult.errorMsg("用户名或密码不正确...");
			}
		} else {
			// 1.2 注册
			user.setNickname(user.getUsername());
			user.setFaceImage("");
			user.setFaceImageBig("");
			user.setPassword(MD5Utils.getMD5Str(user.getPassword()));
			userResult = userService.saveUser(user);
		}

		UsersVO userVO = new UsersVO();
		BeanUtils.copyProperties(userResult, userVO);

		return GeekJSONResult.ok(userVO);
	}

	/**
	 * 上传用户头像
	 * @param userBO
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/uploadFaceBase64")
	public GeekJSONResult uploadFaceBase64(@RequestBody UsersBO userBO) throws Exception {

		// 获取前端传过来的base64字符串, 然后转换为文件对象再上传
		String base64Data = userBO.getFaceData();
		String userFacePath = "/up/" + userBO.getUserId() + "userface64.png";
		FileUtils.base64ToFile(userFacePath, base64Data);

		// 上传文件到fastdfs
		MultipartFile faceFile = FileUtils.fileToMultipart(userFacePath);
		String url = fastDFSClient.uploadBase64(faceFile);
		System.out.println(url);

		// 获取缩略图的url
		String thump = "_80x80.";
		String arr[] = url.split("\\.");
		String thumpImgUrl = arr[0] + thump + arr[1];

		// 更新用户头像
		Users user = new Users();
		user.setId(userBO.getUserId());
		user.setFaceImage(thumpImgUrl);
		user.setFaceImageBig(url);

		Users result = userService.updateUserInfo(user);

		return GeekJSONResult.ok(result);
	}

	/**
	 * 设置用户昵称
	 * @param userBO
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/setNickname")
	public GeekJSONResult setNickname(@RequestBody UsersBO userBO) throws Exception {

		Users user = new Users();
		user.setId(userBO.getUserId());
		user.setNickname(userBO.getNickname());

		Users result = userService.updateUserInfo(user);

		return GeekJSONResult.ok(result);
	}

	/**
	 * 搜索好友接口, 根据账号做匹配查询而不是模糊查询
	 * @param myUserId
	 * @param friendUsername
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/search")
	public GeekJSONResult searchUser(String myUserId, String friendUsername)
			throws Exception {

		// 0. 判断 myUserId friendUsername 不能为空
		if (StringUtils.isBlank(myUserId)
				|| StringUtils.isBlank(friendUsername)) {
			return GeekJSONResult.errorMsg("");
		}

		// 前置条件 - 1. 搜索的用户如果不存在，返回[无此用户]
		// 前置条件 - 2. 搜索账号是你自己，返回[不能添加自己]
		// 前置条件 - 3. 搜索的朋友已经是你的好友，返回[该用户已经是你的好友]
		Integer status = userService.preconditionSearchFriends(myUserId, friendUsername);
		if (status == SearchFriendsStatusEnum.SUCCESS.status) {
			Users user = userService.queryUserInfoByUsername(friendUsername);
			UsersVO userVO = new UsersVO();
			BeanUtils.copyProperties(user, userVO);
			return GeekJSONResult.ok(userVO);
		} else {
			String errorMsg = SearchFriendsStatusEnum.getMsgByKey(status);
			return GeekJSONResult.errorMsg(errorMsg);
		}
	}


	/**
	 * 发送添加好友的请求
	 *
	 * @param myUserId
	 * @param friendUsername
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/addFriendRequest")
	public GeekJSONResult addFriendRequest(String myUserId, String friendUsername)
			throws Exception {

		// 0. 判断 myUserId friendUsername 不能为空
		if (StringUtils.isBlank(myUserId)
				|| StringUtils.isBlank(friendUsername)) {
			return GeekJSONResult.errorMsg("");
		}

		// 前置条件 - 1. 搜索的用户如果不存在，返回[无此用户]
		// 前置条件 - 2. 搜索账号是你自己，返回[不能添加自己]
		// 前置条件 - 3. 搜索的朋友已经是你的好友，返回[该用户已经是你的好友]
		Integer status = userService.preconditionSearchFriends(myUserId, friendUsername);
		if (status == SearchFriendsStatusEnum.SUCCESS.status) {
			userService.sendFriendRequest(myUserId, friendUsername);
		} else {
			String errorMsg = SearchFriendsStatusEnum.getMsgByKey(status);
			return GeekJSONResult.errorMsg(errorMsg);
		}

		return GeekJSONResult.ok();
	}

	/**
	 * 发送添加好友的请求
	 *
	 * @param userId
	 * @return
	 */
	@PostMapping("/queryFriendRequests")
	public GeekJSONResult queryFriendRequests(String userId) {

		// 0. 判断不能为空
		if (StringUtils.isBlank(userId)) {
			return GeekJSONResult.errorMsg("");
		}

		// 1. 查询用户接受到的朋友申请
		return GeekJSONResult.ok(userService.queryFriendRequestList(userId));
	}


	/**
	 * 接受方 通过或者忽略朋友请求
	 *
	 * @param acceptUserId
	 * @param sendUserId
	 * @param operType
	 * @return
	 */
	@PostMapping("/operFriendRequest")
	public GeekJSONResult operFriendRequest(String acceptUserId, String sendUserId,
												Integer operType) {

		// 0. acceptUserId sendUserId operType 判断不能为空
		if (StringUtils.isBlank(acceptUserId)
				|| StringUtils.isBlank(sendUserId)
				|| operType == null) {
			return GeekJSONResult.errorMsg("");
		}

		// 1. 如果operType 没有对应的枚举值，则直接抛出空错误信息
		if (StringUtils.isBlank(OperatorFriendRequestTypeEnum.getMsgByType(operType))) {
			return GeekJSONResult.errorMsg("");
		}

		if (operType == OperatorFriendRequestTypeEnum.IGNORE.type) {
			// 2. 判断如果忽略好友请求，则直接删除好友请求的数据库表记录
			userService.deleteFriendRequest(sendUserId, acceptUserId);
		} else if (operType == OperatorFriendRequestTypeEnum.PASS.type) {
			// 3. 判断如果是通过好友请求，则互相增加好友记录到数据库对应的表
			//	   然后删除好友请求的数据库表记录
			userService.passFriendRequest(sendUserId, acceptUserId);
		}

		// 4. 数据库查询好友列表
		List<MyFriendsVO> myFirends = userService.queryMyFriends(acceptUserId);

		return GeekJSONResult.ok(myFirends);
	}

	/**
	 * 查询我的好友列表
	 * @param userId
	 * @return
	 */
	@PostMapping("/myFriends")
	public GeekJSONResult myFriends(String userId) {
		// 0. userId 判断不能为空
		if (StringUtils.isBlank(userId)) {
			return GeekJSONResult.errorMsg("");
		}

		// 1. 数据库查询好友列表
		List<MyFriendsVO> myFirends = userService.queryMyFriends(userId);

		return GeekJSONResult.ok(myFirends);
	}

	/**
	 * 用户手机端获取未签收的消息列表
	 *
	 * @param acceptUserId
	 * @return
	 */
	@PostMapping("/getUnReadMsgList")
	public GeekJSONResult getUnReadMsgList(String acceptUserId) {
		// 0. userId 判断不能为空
		if (StringUtils.isBlank(acceptUserId)) {
			return GeekJSONResult.errorMsg("");
		}

		// 查询列表
		List<com.geekerstar.pojo.ChatMsg> unreadMsgList = userService.getUnReadMsgList(acceptUserId);

		return GeekJSONResult.ok(unreadMsgList);
	}
}
