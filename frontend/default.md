# 接口文档


**简介**:接口文档


**HOST**:121.199.77.168:8090


**联系人**:


**Version**:1.0


**接口路径**:/v2/api-docs


[TOC]






# file-controller


## testDownloadFile


**接口地址**:`/api/file/test/download/`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|filepath|filepath|query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


暂无


**响应示例**:
```javascript

```


## testUploadFile


**接口地址**:`/api/file/test/upload`


**请求方式**:`POST`


**请求数据类型**:`multipart/form-data`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|file|file|formData|true|file||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«string»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": "",
	"message": ""
}
```


# main-controller


## health


**接口地址**:`/api/health`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«string»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": "",
	"message": ""
}
```


# picture-controller


## deletePicture


**接口地址**:`/api/picture/delete`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|deleteRequest|deleteRequest|body|true|DeleteRequest|DeleteRequest|
|&emsp;&emsp;id|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## editPicture


**接口地址**:`/api/picture/edit`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "category": "",
  "id": 0,
  "introduction": "",
  "name": "",
  "tags": []
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureEditRequest|pictureEditRequest|body|true|PictureEditRequest|PictureEditRequest|
|&emsp;&emsp;category|||false|string||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;introduction|||false|string||
|&emsp;&emsp;name|||false|string||
|&emsp;&emsp;tags|||false|array|string|


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## editPictureByBatch


**接口地址**:`/api/picture/edit/batch`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "category": "",
  "nameRule": "",
  "pictureIdList": [],
  "spaceId": 0,
  "tags": []
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureEditByBatchRequest|pictureEditByBatchRequest|body|true|PictureEditByBatchRequest|PictureEditByBatchRequest|
|&emsp;&emsp;category|||false|string||
|&emsp;&emsp;nameRule|||false|string||
|&emsp;&emsp;pictureIdList|||false|array|integer(int64)|
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;tags|||false|array|string|


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## getPictureById


**接口地址**:`/api/picture/get`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|id|query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Picture»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Picture|Picture|
|&emsp;&emsp;category||string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;introduction||string||
|&emsp;&emsp;isDelete||integer(int32)||
|&emsp;&emsp;name||string||
|&emsp;&emsp;picColor||string||
|&emsp;&emsp;picFormat||string||
|&emsp;&emsp;picHeight||integer(int32)||
|&emsp;&emsp;picScale||number(double)||
|&emsp;&emsp;picSize||integer(int64)||
|&emsp;&emsp;picWidth||integer(int32)||
|&emsp;&emsp;reviewMessage||string||
|&emsp;&emsp;reviewStatus||integer(int32)||
|&emsp;&emsp;reviewTime||string(date-time)||
|&emsp;&emsp;reviewerId||integer(int64)||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;tags||string||
|&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;url||string||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"category": "",
		"createTime": "",
		"editTime": "",
		"id": 0,
		"introduction": "",
		"isDelete": 0,
		"name": "",
		"picColor": "",
		"picFormat": "",
		"picHeight": 0,
		"picScale": 0,
		"picSize": 0,
		"picWidth": 0,
		"reviewMessage": "",
		"reviewStatus": 0,
		"reviewTime": "",
		"reviewerId": 0,
		"spaceId": 0,
		"tags": "",
		"thumbnailUrl": "",
		"updateTime": "",
		"url": "",
		"userId": 0
	},
	"message": ""
}
```


## getPictureVOById


**接口地址**:`/api/picture/get/vo`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|id|query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«PictureVO»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||PictureVO|PictureVO|
|&emsp;&emsp;category||string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;introduction||string||
|&emsp;&emsp;name||string||
|&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;picColor||string||
|&emsp;&emsp;picFormat||string||
|&emsp;&emsp;picHeight||integer(int32)||
|&emsp;&emsp;picScale||number(double)||
|&emsp;&emsp;picSize||integer(int64)||
|&emsp;&emsp;picWidth||integer(int32)||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;tags||array|string|
|&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;url||string||
|&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"category": "",
		"createTime": "",
		"editTime": "",
		"id": 0,
		"introduction": "",
		"name": "",
		"permissionList": [],
		"picColor": "",
		"picFormat": "",
		"picHeight": 0,
		"picScale": 0,
		"picSize": 0,
		"picWidth": 0,
		"spaceId": 0,
		"tags": [],
		"thumbnailUrl": "",
		"updateTime": "",
		"url": "",
		"user": {
			"createTime": "",
			"id": 0,
			"userAccount": "",
			"userAvatar": "",
			"userName": "",
			"userProfile": "",
			"userRole": "",
			"vipCode": "",
			"vipExpireTime": "",
			"vipNumber": 0
		},
		"userId": 0
	},
	"message": ""
}
```


## listPictureByPage


**接口地址**:`/api/picture/list/page`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "category": "",
  "current": 0,
  "endEditTime": "",
  "id": 0,
  "introduction": "",
  "name": "",
  "nullSpaceId": true,
  "pageSize": 0,
  "picFormat": "",
  "picHeight": 0,
  "picScale": 0,
  "picSize": 0,
  "picWidth": 0,
  "reviewMessage": "",
  "reviewStatus": 0,
  "reviewTime": "",
  "reviewerId": 0,
  "searchText": "",
  "sortField": "",
  "sortOrder": "",
  "spaceId": 0,
  "startEditTime": "",
  "tags": [],
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureQueryRequest|pictureQueryRequest|body|true|PictureQueryRequest|PictureQueryRequest|
|&emsp;&emsp;category|||false|string||
|&emsp;&emsp;current|||false|integer(int32)||
|&emsp;&emsp;endEditTime|||false|string(date-time)||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;introduction|||false|string||
|&emsp;&emsp;name|||false|string||
|&emsp;&emsp;nullSpaceId|||false|boolean||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;picFormat|||false|string||
|&emsp;&emsp;picHeight|||false|integer(int32)||
|&emsp;&emsp;picScale|||false|number(double)||
|&emsp;&emsp;picSize|||false|integer(int64)||
|&emsp;&emsp;picWidth|||false|integer(int32)||
|&emsp;&emsp;reviewMessage|||false|string||
|&emsp;&emsp;reviewStatus|||false|integer(int32)||
|&emsp;&emsp;reviewTime|||false|string(date-time)||
|&emsp;&emsp;reviewerId|||false|integer(int64)||
|&emsp;&emsp;searchText|||false|string||
|&emsp;&emsp;sortField|||false|string||
|&emsp;&emsp;sortOrder|||false|string||
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;startEditTime|||false|string(date-time)||
|&emsp;&emsp;tags|||false|array|string|
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Page«Picture»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Page«Picture»|Page«Picture»|
|&emsp;&emsp;current||integer(int64)||
|&emsp;&emsp;pages||integer(int64)||
|&emsp;&emsp;records||array|Picture|
|&emsp;&emsp;&emsp;&emsp;category||string||
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;editTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;introduction||string||
|&emsp;&emsp;&emsp;&emsp;isDelete||integer||
|&emsp;&emsp;&emsp;&emsp;name||string||
|&emsp;&emsp;&emsp;&emsp;picColor||string||
|&emsp;&emsp;&emsp;&emsp;picFormat||string||
|&emsp;&emsp;&emsp;&emsp;picHeight||integer||
|&emsp;&emsp;&emsp;&emsp;picScale||number||
|&emsp;&emsp;&emsp;&emsp;picSize||integer||
|&emsp;&emsp;&emsp;&emsp;picWidth||integer||
|&emsp;&emsp;&emsp;&emsp;reviewMessage||string||
|&emsp;&emsp;&emsp;&emsp;reviewStatus||integer||
|&emsp;&emsp;&emsp;&emsp;reviewTime||string||
|&emsp;&emsp;&emsp;&emsp;reviewerId||integer||
|&emsp;&emsp;&emsp;&emsp;spaceId||integer||
|&emsp;&emsp;&emsp;&emsp;tags||string||
|&emsp;&emsp;&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;&emsp;&emsp;updateTime||string||
|&emsp;&emsp;&emsp;&emsp;url||string||
|&emsp;&emsp;&emsp;&emsp;userId||integer||
|&emsp;&emsp;size||integer(int64)||
|&emsp;&emsp;total||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"current": 0,
		"pages": 0,
		"records": [
			{
				"category": "",
				"createTime": "",
				"editTime": "",
				"id": 0,
				"introduction": "",
				"isDelete": 0,
				"name": "",
				"picColor": "",
				"picFormat": "",
				"picHeight": 0,
				"picScale": 0,
				"picSize": 0,
				"picWidth": 0,
				"reviewMessage": "",
				"reviewStatus": 0,
				"reviewTime": "",
				"reviewerId": 0,
				"spaceId": 0,
				"tags": "",
				"thumbnailUrl": "",
				"updateTime": "",
				"url": "",
				"userId": 0
			}
		],
		"size": 0,
		"total": 0
	},
	"message": ""
}
```


## listPictureVOByPage


**接口地址**:`/api/picture/list/page/vo`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "category": "",
  "current": 0,
  "endEditTime": "",
  "id": 0,
  "introduction": "",
  "name": "",
  "nullSpaceId": true,
  "pageSize": 0,
  "picFormat": "",
  "picHeight": 0,
  "picScale": 0,
  "picSize": 0,
  "picWidth": 0,
  "reviewMessage": "",
  "reviewStatus": 0,
  "reviewTime": "",
  "reviewerId": 0,
  "searchText": "",
  "sortField": "",
  "sortOrder": "",
  "spaceId": 0,
  "startEditTime": "",
  "tags": [],
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureQueryRequest|pictureQueryRequest|body|true|PictureQueryRequest|PictureQueryRequest|
|&emsp;&emsp;category|||false|string||
|&emsp;&emsp;current|||false|integer(int32)||
|&emsp;&emsp;endEditTime|||false|string(date-time)||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;introduction|||false|string||
|&emsp;&emsp;name|||false|string||
|&emsp;&emsp;nullSpaceId|||false|boolean||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;picFormat|||false|string||
|&emsp;&emsp;picHeight|||false|integer(int32)||
|&emsp;&emsp;picScale|||false|number(double)||
|&emsp;&emsp;picSize|||false|integer(int64)||
|&emsp;&emsp;picWidth|||false|integer(int32)||
|&emsp;&emsp;reviewMessage|||false|string||
|&emsp;&emsp;reviewStatus|||false|integer(int32)||
|&emsp;&emsp;reviewTime|||false|string(date-time)||
|&emsp;&emsp;reviewerId|||false|integer(int64)||
|&emsp;&emsp;searchText|||false|string||
|&emsp;&emsp;sortField|||false|string||
|&emsp;&emsp;sortOrder|||false|string||
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;startEditTime|||false|string(date-time)||
|&emsp;&emsp;tags|||false|array|string|
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Page«PictureVO»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Page«PictureVO»|Page«PictureVO»|
|&emsp;&emsp;current||integer(int64)||
|&emsp;&emsp;pages||integer(int64)||
|&emsp;&emsp;records||array|PictureVO|
|&emsp;&emsp;&emsp;&emsp;category||string||
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;editTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;introduction||string||
|&emsp;&emsp;&emsp;&emsp;name||string||
|&emsp;&emsp;&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;&emsp;&emsp;picColor||string||
|&emsp;&emsp;&emsp;&emsp;picFormat||string||
|&emsp;&emsp;&emsp;&emsp;picHeight||integer||
|&emsp;&emsp;&emsp;&emsp;picScale||number||
|&emsp;&emsp;&emsp;&emsp;picSize||integer||
|&emsp;&emsp;&emsp;&emsp;picWidth||integer||
|&emsp;&emsp;&emsp;&emsp;spaceId||integer||
|&emsp;&emsp;&emsp;&emsp;tags||array|string|
|&emsp;&emsp;&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;&emsp;&emsp;updateTime||string||
|&emsp;&emsp;&emsp;&emsp;url||string||
|&emsp;&emsp;&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;&emsp;&emsp;userId||integer||
|&emsp;&emsp;size||integer(int64)||
|&emsp;&emsp;total||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"current": 0,
		"pages": 0,
		"records": [
			{
				"category": "",
				"createTime": "",
				"editTime": "",
				"id": 0,
				"introduction": "",
				"name": "",
				"permissionList": [],
				"picColor": "",
				"picFormat": "",
				"picHeight": 0,
				"picScale": 0,
				"picSize": 0,
				"picWidth": 0,
				"spaceId": 0,
				"tags": [],
				"thumbnailUrl": "",
				"updateTime": "",
				"url": "",
				"user": {
					"createTime": "",
					"id": 0,
					"userAccount": "",
					"userAvatar": "",
					"userName": "",
					"userProfile": "",
					"userRole": "",
					"vipCode": "",
					"vipExpireTime": "",
					"vipNumber": 0
				},
				"userId": 0
			}
		],
		"size": 0,
		"total": 0
	},
	"message": ""
}
```


## listPictureVOByPageWithCache


**接口地址**:`/api/picture/list/page/vo/cache`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "category": "",
  "current": 0,
  "endEditTime": "",
  "id": 0,
  "introduction": "",
  "name": "",
  "nullSpaceId": true,
  "pageSize": 0,
  "picFormat": "",
  "picHeight": 0,
  "picScale": 0,
  "picSize": 0,
  "picWidth": 0,
  "reviewMessage": "",
  "reviewStatus": 0,
  "reviewTime": "",
  "reviewerId": 0,
  "searchText": "",
  "sortField": "",
  "sortOrder": "",
  "spaceId": 0,
  "startEditTime": "",
  "tags": [],
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureQueryRequest|pictureQueryRequest|body|true|PictureQueryRequest|PictureQueryRequest|
|&emsp;&emsp;category|||false|string||
|&emsp;&emsp;current|||false|integer(int32)||
|&emsp;&emsp;endEditTime|||false|string(date-time)||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;introduction|||false|string||
|&emsp;&emsp;name|||false|string||
|&emsp;&emsp;nullSpaceId|||false|boolean||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;picFormat|||false|string||
|&emsp;&emsp;picHeight|||false|integer(int32)||
|&emsp;&emsp;picScale|||false|number(double)||
|&emsp;&emsp;picSize|||false|integer(int64)||
|&emsp;&emsp;picWidth|||false|integer(int32)||
|&emsp;&emsp;reviewMessage|||false|string||
|&emsp;&emsp;reviewStatus|||false|integer(int32)||
|&emsp;&emsp;reviewTime|||false|string(date-time)||
|&emsp;&emsp;reviewerId|||false|integer(int64)||
|&emsp;&emsp;searchText|||false|string||
|&emsp;&emsp;sortField|||false|string||
|&emsp;&emsp;sortOrder|||false|string||
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;startEditTime|||false|string(date-time)||
|&emsp;&emsp;tags|||false|array|string|
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Page«PictureVO»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Page«PictureVO»|Page«PictureVO»|
|&emsp;&emsp;current||integer(int64)||
|&emsp;&emsp;pages||integer(int64)||
|&emsp;&emsp;records||array|PictureVO|
|&emsp;&emsp;&emsp;&emsp;category||string||
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;editTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;introduction||string||
|&emsp;&emsp;&emsp;&emsp;name||string||
|&emsp;&emsp;&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;&emsp;&emsp;picColor||string||
|&emsp;&emsp;&emsp;&emsp;picFormat||string||
|&emsp;&emsp;&emsp;&emsp;picHeight||integer||
|&emsp;&emsp;&emsp;&emsp;picScale||number||
|&emsp;&emsp;&emsp;&emsp;picSize||integer||
|&emsp;&emsp;&emsp;&emsp;picWidth||integer||
|&emsp;&emsp;&emsp;&emsp;spaceId||integer||
|&emsp;&emsp;&emsp;&emsp;tags||array|string|
|&emsp;&emsp;&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;&emsp;&emsp;updateTime||string||
|&emsp;&emsp;&emsp;&emsp;url||string||
|&emsp;&emsp;&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;&emsp;&emsp;userId||integer||
|&emsp;&emsp;size||integer(int64)||
|&emsp;&emsp;total||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"current": 0,
		"pages": 0,
		"records": [
			{
				"category": "",
				"createTime": "",
				"editTime": "",
				"id": 0,
				"introduction": "",
				"name": "",
				"permissionList": [],
				"picColor": "",
				"picFormat": "",
				"picHeight": 0,
				"picScale": 0,
				"picSize": 0,
				"picWidth": 0,
				"spaceId": 0,
				"tags": [],
				"thumbnailUrl": "",
				"updateTime": "",
				"url": "",
				"user": {
					"createTime": "",
					"id": 0,
					"userAccount": "",
					"userAvatar": "",
					"userName": "",
					"userProfile": "",
					"userRole": "",
					"vipCode": "",
					"vipExpireTime": "",
					"vipNumber": 0
				},
				"userId": 0
			}
		],
		"size": 0,
		"total": 0
	},
	"message": ""
}
```


## createPictureOutPaintingTask


**接口地址**:`/api/picture/out_painting/create_task`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "parameters": {
    "addWatermark": true,
    "angle": 0,
    "bestQuality": true,
    "bottomOffset": 0,
    "leftOffset": 0,
    "limitImageSize": true,
    "outputRatio": "",
    "rightOffset": 0,
    "topOffset": 0,
    "xScale": 0,
    "yScale": 0
  },
  "pictureId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|createPictureOutPaintingTaskRequest|createPictureOutPaintingTaskRequest|body|true|CreatePictureOutPaintingTaskRequest|CreatePictureOutPaintingTaskRequest|
|&emsp;&emsp;parameters|||false|Parameters|Parameters|
|&emsp;&emsp;&emsp;&emsp;addWatermark|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;angle|||false|integer||
|&emsp;&emsp;&emsp;&emsp;bestQuality|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;bottomOffset|||false|integer||
|&emsp;&emsp;&emsp;&emsp;leftOffset|||false|integer||
|&emsp;&emsp;&emsp;&emsp;limitImageSize|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;outputRatio|||false|string||
|&emsp;&emsp;&emsp;&emsp;rightOffset|||false|integer||
|&emsp;&emsp;&emsp;&emsp;topOffset|||false|integer||
|&emsp;&emsp;&emsp;&emsp;xScale|||false|number||
|&emsp;&emsp;&emsp;&emsp;yScale|||false|number||
|&emsp;&emsp;pictureId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«CreateOutPaintingTaskResponse»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||CreateOutPaintingTaskResponse|CreateOutPaintingTaskResponse|
|&emsp;&emsp;code||string||
|&emsp;&emsp;message||string||
|&emsp;&emsp;output||Output|Output|
|&emsp;&emsp;&emsp;&emsp;taskId||string||
|&emsp;&emsp;&emsp;&emsp;taskStatus||string||
|&emsp;&emsp;requestId||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"code": "",
		"message": "",
		"output": {
			"taskId": "",
			"taskStatus": ""
		},
		"requestId": ""
	},
	"message": ""
}
```


## getPictureOutPaintingTask


**接口地址**:`/api/picture/out_painting/get_task`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|taskId|taskId|query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«GetOutPaintingTaskResponse»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||GetOutPaintingTaskResponse|GetOutPaintingTaskResponse|
|&emsp;&emsp;output||Output_1|Output_1|
|&emsp;&emsp;&emsp;&emsp;code||string||
|&emsp;&emsp;&emsp;&emsp;endTime||string||
|&emsp;&emsp;&emsp;&emsp;message||string||
|&emsp;&emsp;&emsp;&emsp;outputImageUrl||string||
|&emsp;&emsp;&emsp;&emsp;scheduledTime||string||
|&emsp;&emsp;&emsp;&emsp;submitTime||string||
|&emsp;&emsp;&emsp;&emsp;taskId||string||
|&emsp;&emsp;&emsp;&emsp;taskMetrics||TaskMetrics|TaskMetrics|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;failed||integer||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;succeeded||integer||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;total||integer||
|&emsp;&emsp;&emsp;&emsp;taskStatus||string||
|&emsp;&emsp;requestId||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"output": {
			"code": "",
			"endTime": "",
			"message": "",
			"outputImageUrl": "",
			"scheduledTime": "",
			"submitTime": "",
			"taskId": "",
			"taskMetrics": {
				"failed": 0,
				"succeeded": 0,
				"total": 0
			},
			"taskStatus": ""
		},
		"requestId": ""
	},
	"message": ""
}
```


## doPictureReview


**接口地址**:`/api/picture/review`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "reviewMessage": "",
  "reviewStatus": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureReviewRequest|pictureReviewRequest|body|true|PictureReviewRequest|PictureReviewRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;reviewMessage|||false|string||
|&emsp;&emsp;reviewStatus|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## searchPictureByColor


**接口地址**:`/api/picture/search/color`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "picColor": "",
  "spaceId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|searchPictureByColorRequest|searchPictureByColorRequest|body|true|SearchPictureByColorRequest|SearchPictureByColorRequest|
|&emsp;&emsp;picColor|||false|string||
|&emsp;&emsp;spaceId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«PictureVO»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|PictureVO|
|&emsp;&emsp;category||string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;introduction||string||
|&emsp;&emsp;name||string||
|&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;picColor||string||
|&emsp;&emsp;picFormat||string||
|&emsp;&emsp;picHeight||integer(int32)||
|&emsp;&emsp;picScale||number(double)||
|&emsp;&emsp;picSize||integer(int64)||
|&emsp;&emsp;picWidth||integer(int32)||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;tags||array|string|
|&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;url||string||
|&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"category": "",
			"createTime": "",
			"editTime": "",
			"id": 0,
			"introduction": "",
			"name": "",
			"permissionList": [],
			"picColor": "",
			"picFormat": "",
			"picHeight": 0,
			"picScale": 0,
			"picSize": 0,
			"picWidth": 0,
			"spaceId": 0,
			"tags": [],
			"thumbnailUrl": "",
			"updateTime": "",
			"url": "",
			"user": {
				"createTime": "",
				"id": 0,
				"userAccount": "",
				"userAvatar": "",
				"userName": "",
				"userProfile": "",
				"userRole": "",
				"vipCode": "",
				"vipExpireTime": "",
				"vipNumber": 0
			},
			"userId": 0
		}
	],
	"message": ""
}
```


## searchPictureByPicture


**接口地址**:`/api/picture/search/picture`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "pictureId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|searchPictureByPictureRequest|searchPictureByPictureRequest|body|true|SearchPictureByPictureRequest|SearchPictureByPictureRequest|
|&emsp;&emsp;pictureId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«ImageSearchResult»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|ImageSearchResult|
|&emsp;&emsp;fromUrl||string||
|&emsp;&emsp;thumbUrl||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"fromUrl": "",
			"thumbUrl": ""
		}
	],
	"message": ""
}
```


## listPictureTagCategory


**接口地址**:`/api/picture/tag_category`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«PictureTagCategory»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||PictureTagCategory|PictureTagCategory|
|&emsp;&emsp;categoryList||array|string|
|&emsp;&emsp;tagList||array|string|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"categoryList": [],
		"tagList": []
	},
	"message": ""
}
```


## updatePicture


**接口地址**:`/api/picture/update`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "category": "",
  "id": 0,
  "introduction": "",
  "name": "",
  "tags": []
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureUpdateRequest|pictureUpdateRequest|body|true|PictureUpdateRequest|PictureUpdateRequest|
|&emsp;&emsp;category|||false|string||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;introduction|||false|string||
|&emsp;&emsp;name|||false|string||
|&emsp;&emsp;tags|||false|array|string|


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## uploadPicture


**接口地址**:`/api/picture/upload`


**请求方式**:`POST`


**请求数据类型**:`multipart/form-data`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|file|file|formData|true|file||
|fileUrl||query|false|string||
|id||query|false|integer(int64)||
|picName||query|false|string||
|spaceId||query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«PictureVO»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||PictureVO|PictureVO|
|&emsp;&emsp;category||string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;introduction||string||
|&emsp;&emsp;name||string||
|&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;picColor||string||
|&emsp;&emsp;picFormat||string||
|&emsp;&emsp;picHeight||integer(int32)||
|&emsp;&emsp;picScale||number(double)||
|&emsp;&emsp;picSize||integer(int64)||
|&emsp;&emsp;picWidth||integer(int32)||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;tags||array|string|
|&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;url||string||
|&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"category": "",
		"createTime": "",
		"editTime": "",
		"id": 0,
		"introduction": "",
		"name": "",
		"permissionList": [],
		"picColor": "",
		"picFormat": "",
		"picHeight": 0,
		"picScale": 0,
		"picSize": 0,
		"picWidth": 0,
		"spaceId": 0,
		"tags": [],
		"thumbnailUrl": "",
		"updateTime": "",
		"url": "",
		"user": {
			"createTime": "",
			"id": 0,
			"userAccount": "",
			"userAvatar": "",
			"userName": "",
			"userProfile": "",
			"userRole": "",
			"vipCode": "",
			"vipExpireTime": "",
			"vipNumber": 0
		},
		"userId": 0
	},
	"message": ""
}
```


## uploadPictureByBatch


**接口地址**:`/api/picture/upload/batch`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "count": 0,
  "namePrefix": "",
  "searchText": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureUploadByBatchRequest|pictureUploadByBatchRequest|body|true|PictureUploadByBatchRequest|PictureUploadByBatchRequest|
|&emsp;&emsp;count|||false|integer(int32)||
|&emsp;&emsp;namePrefix|||false|string||
|&emsp;&emsp;searchText|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«int»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||integer(int32)|integer(int32)|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": 0,
	"message": ""
}
```


## uploadPictureByUrl


**接口地址**:`/api/picture/upload/url`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "fileUrl": "",
  "id": 0,
  "picName": "",
  "spaceId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|pictureUploadRequest|pictureUploadRequest|body|true|PictureUploadRequest|PictureUploadRequest|
|&emsp;&emsp;fileUrl|||false|string||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;picName|||false|string||
|&emsp;&emsp;spaceId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«PictureVO»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||PictureVO|PictureVO|
|&emsp;&emsp;category||string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;introduction||string||
|&emsp;&emsp;name||string||
|&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;picColor||string||
|&emsp;&emsp;picFormat||string||
|&emsp;&emsp;picHeight||integer(int32)||
|&emsp;&emsp;picScale||number(double)||
|&emsp;&emsp;picSize||integer(int64)||
|&emsp;&emsp;picWidth||integer(int32)||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;tags||array|string|
|&emsp;&emsp;thumbnailUrl||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;url||string||
|&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"category": "",
		"createTime": "",
		"editTime": "",
		"id": 0,
		"introduction": "",
		"name": "",
		"permissionList": [],
		"picColor": "",
		"picFormat": "",
		"picHeight": 0,
		"picScale": 0,
		"picSize": 0,
		"picWidth": 0,
		"spaceId": 0,
		"tags": [],
		"thumbnailUrl": "",
		"updateTime": "",
		"url": "",
		"user": {
			"createTime": "",
			"id": 0,
			"userAccount": "",
			"userAvatar": "",
			"userName": "",
			"userProfile": "",
			"userRole": "",
			"vipCode": "",
			"vipExpireTime": "",
			"vipNumber": 0
		},
		"userId": 0
	},
	"message": ""
}
```


# space-analyze-controller


## getSpaceCategoryAnalyze


**接口地址**:`/api/space/analyze/category`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "queryAll": true,
  "queryPublic": true,
  "spaceId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceCategoryAnalyzeRequest|spaceCategoryAnalyzeRequest|body|true|SpaceCategoryAnalyzeRequest|SpaceCategoryAnalyzeRequest|
|&emsp;&emsp;queryAll|||false|boolean||
|&emsp;&emsp;queryPublic|||false|boolean||
|&emsp;&emsp;spaceId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«SpaceCategoryAnalyzeResponse»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|SpaceCategoryAnalyzeResponse|
|&emsp;&emsp;category||string||
|&emsp;&emsp;count||integer(int64)||
|&emsp;&emsp;totalSize||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"category": "",
			"count": 0,
			"totalSize": 0
		}
	],
	"message": ""
}
```


## getSpaceRankAnalyze


**接口地址**:`/api/space/analyze/rank`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "topN": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceRankAnalyzeRequest|spaceRankAnalyzeRequest|body|true|SpaceRankAnalyzeRequest|SpaceRankAnalyzeRequest|
|&emsp;&emsp;topN|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«Space»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|Space|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;isDelete||integer(int32)||
|&emsp;&emsp;maxCount||integer(int64)||
|&emsp;&emsp;maxSize||integer(int64)||
|&emsp;&emsp;spaceLevel||integer(int32)||
|&emsp;&emsp;spaceName||string||
|&emsp;&emsp;spaceType||integer(int32)||
|&emsp;&emsp;totalCount||integer(int64)||
|&emsp;&emsp;totalSize||integer(int64)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"createTime": "",
			"editTime": "",
			"id": 0,
			"isDelete": 0,
			"maxCount": 0,
			"maxSize": 0,
			"spaceLevel": 0,
			"spaceName": "",
			"spaceType": 0,
			"totalCount": 0,
			"totalSize": 0,
			"updateTime": "",
			"userId": 0
		}
	],
	"message": ""
}
```


## getSpaceSizeAnalyze


**接口地址**:`/api/space/analyze/size`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "queryAll": true,
  "queryPublic": true,
  "spaceId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceSizeAnalyzeRequest|spaceSizeAnalyzeRequest|body|true|SpaceSizeAnalyzeRequest|SpaceSizeAnalyzeRequest|
|&emsp;&emsp;queryAll|||false|boolean||
|&emsp;&emsp;queryPublic|||false|boolean||
|&emsp;&emsp;spaceId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«SpaceSizeAnalyzeResponse»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|SpaceSizeAnalyzeResponse|
|&emsp;&emsp;count||integer(int64)||
|&emsp;&emsp;sizeRange||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"count": 0,
			"sizeRange": ""
		}
	],
	"message": ""
}
```


## getSpaceTagAnalyze


**接口地址**:`/api/space/analyze/tag`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "queryAll": true,
  "queryPublic": true,
  "spaceId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceTagAnalyzeRequest|spaceTagAnalyzeRequest|body|true|SpaceTagAnalyzeRequest|SpaceTagAnalyzeRequest|
|&emsp;&emsp;queryAll|||false|boolean||
|&emsp;&emsp;queryPublic|||false|boolean||
|&emsp;&emsp;spaceId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«SpaceTagAnalyzeResponse»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|SpaceTagAnalyzeResponse|
|&emsp;&emsp;count||integer(int64)||
|&emsp;&emsp;tag||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"count": 0,
			"tag": ""
		}
	],
	"message": ""
}
```


## getSpaceUsageAnalyze


**接口地址**:`/api/space/analyze/usage`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "queryAll": true,
  "queryPublic": true,
  "spaceId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceUsageAnalyzeRequest|spaceUsageAnalyzeRequest|body|true|SpaceUsageAnalyzeRequest|SpaceUsageAnalyzeRequest|
|&emsp;&emsp;queryAll|||false|boolean||
|&emsp;&emsp;queryPublic|||false|boolean||
|&emsp;&emsp;spaceId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«SpaceUsageAnalyzeResponse»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||SpaceUsageAnalyzeResponse|SpaceUsageAnalyzeResponse|
|&emsp;&emsp;countUsageRatio||number(double)||
|&emsp;&emsp;maxCount||integer(int64)||
|&emsp;&emsp;maxSize||integer(int64)||
|&emsp;&emsp;sizeUsageRatio||number(double)||
|&emsp;&emsp;usedCount||integer(int64)||
|&emsp;&emsp;usedSize||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"countUsageRatio": 0,
		"maxCount": 0,
		"maxSize": 0,
		"sizeUsageRatio": 0,
		"usedCount": 0,
		"usedSize": 0
	},
	"message": ""
}
```


## getSpaceUserAnalyze


**接口地址**:`/api/space/analyze/user`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "queryAll": true,
  "queryPublic": true,
  "spaceId": 0,
  "timeDimension": "",
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceUserAnalyzeRequest|spaceUserAnalyzeRequest|body|true|SpaceUserAnalyzeRequest|SpaceUserAnalyzeRequest|
|&emsp;&emsp;queryAll|||false|boolean||
|&emsp;&emsp;queryPublic|||false|boolean||
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;timeDimension|||false|string||
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«SpaceUserAnalyzeResponse»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|SpaceUserAnalyzeResponse|
|&emsp;&emsp;count||integer(int64)||
|&emsp;&emsp;period||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"count": 0,
			"period": ""
		}
	],
	"message": ""
}
```


# space-controller


## addSpace


**接口地址**:`/api/space/add`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "spaceLevel": 0,
  "spaceName": "",
  "spaceType": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceAddRequest|spaceAddRequest|body|true|SpaceAddRequest|SpaceAddRequest|
|&emsp;&emsp;spaceLevel|||false|integer(int32)||
|&emsp;&emsp;spaceName|||false|string||
|&emsp;&emsp;spaceType|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«long»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||integer(int64)|integer(int64)|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": 0,
	"message": ""
}
```


## deleteSpace


**接口地址**:`/api/space/delete`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|deleteRequest|deleteRequest|body|true|DeleteRequest|DeleteRequest|
|&emsp;&emsp;id|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## editSpace


**接口地址**:`/api/space/edit`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "spaceName": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceEditRequest|spaceEditRequest|body|true|SpaceEditRequest|SpaceEditRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;spaceName|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## getSpaceById


**接口地址**:`/api/space/get`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|id|query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Space»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Space|Space|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;isDelete||integer(int32)||
|&emsp;&emsp;maxCount||integer(int64)||
|&emsp;&emsp;maxSize||integer(int64)||
|&emsp;&emsp;spaceLevel||integer(int32)||
|&emsp;&emsp;spaceName||string||
|&emsp;&emsp;spaceType||integer(int32)||
|&emsp;&emsp;totalCount||integer(int64)||
|&emsp;&emsp;totalSize||integer(int64)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"createTime": "",
		"editTime": "",
		"id": 0,
		"isDelete": 0,
		"maxCount": 0,
		"maxSize": 0,
		"spaceLevel": 0,
		"spaceName": "",
		"spaceType": 0,
		"totalCount": 0,
		"totalSize": 0,
		"updateTime": "",
		"userId": 0
	},
	"message": ""
}
```


## getSpaceVOById


**接口地址**:`/api/space/get/vo`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|id|query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«SpaceVO»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||SpaceVO|SpaceVO|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;maxCount||integer(int64)||
|&emsp;&emsp;maxSize||integer(int64)||
|&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;spaceLevel||integer(int32)||
|&emsp;&emsp;spaceName||string||
|&emsp;&emsp;spaceType||integer(int32)||
|&emsp;&emsp;totalCount||integer(int64)||
|&emsp;&emsp;totalSize||integer(int64)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"createTime": "",
		"editTime": "",
		"id": 0,
		"maxCount": 0,
		"maxSize": 0,
		"permissionList": [],
		"spaceLevel": 0,
		"spaceName": "",
		"spaceType": 0,
		"totalCount": 0,
		"totalSize": 0,
		"updateTime": "",
		"user": {
			"createTime": "",
			"id": 0,
			"userAccount": "",
			"userAvatar": "",
			"userName": "",
			"userProfile": "",
			"userRole": "",
			"vipCode": "",
			"vipExpireTime": "",
			"vipNumber": 0
		},
		"userId": 0
	},
	"message": ""
}
```


## listSpaceLevel


**接口地址**:`/api/space/list/level`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«SpaceLevel»»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|SpaceLevel|
|&emsp;&emsp;maxCount||integer(int64)||
|&emsp;&emsp;maxSize||integer(int64)||
|&emsp;&emsp;text||string||
|&emsp;&emsp;value||integer(int32)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"maxCount": 0,
			"maxSize": 0,
			"text": "",
			"value": 0
		}
	],
	"message": ""
}
```


## listSpaceByPage


**接口地址**:`/api/space/list/page`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "current": 0,
  "id": 0,
  "pageSize": 0,
  "sortField": "",
  "sortOrder": "",
  "spaceLevel": 0,
  "spaceName": "",
  "spaceType": 0,
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceQueryRequest|spaceQueryRequest|body|true|SpaceQueryRequest|SpaceQueryRequest|
|&emsp;&emsp;current|||false|integer(int32)||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;sortField|||false|string||
|&emsp;&emsp;sortOrder|||false|string||
|&emsp;&emsp;spaceLevel|||false|integer(int32)||
|&emsp;&emsp;spaceName|||false|string||
|&emsp;&emsp;spaceType|||false|integer(int32)||
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Page«Space»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Page«Space»|Page«Space»|
|&emsp;&emsp;current||integer(int64)||
|&emsp;&emsp;pages||integer(int64)||
|&emsp;&emsp;records||array|Space|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;editTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;isDelete||integer||
|&emsp;&emsp;&emsp;&emsp;maxCount||integer||
|&emsp;&emsp;&emsp;&emsp;maxSize||integer||
|&emsp;&emsp;&emsp;&emsp;spaceLevel||integer||
|&emsp;&emsp;&emsp;&emsp;spaceName||string||
|&emsp;&emsp;&emsp;&emsp;spaceType||integer||
|&emsp;&emsp;&emsp;&emsp;totalCount||integer||
|&emsp;&emsp;&emsp;&emsp;totalSize||integer||
|&emsp;&emsp;&emsp;&emsp;updateTime||string||
|&emsp;&emsp;&emsp;&emsp;userId||integer||
|&emsp;&emsp;size||integer(int64)||
|&emsp;&emsp;total||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"current": 0,
		"pages": 0,
		"records": [
			{
				"createTime": "",
				"editTime": "",
				"id": 0,
				"isDelete": 0,
				"maxCount": 0,
				"maxSize": 0,
				"spaceLevel": 0,
				"spaceName": "",
				"spaceType": 0,
				"totalCount": 0,
				"totalSize": 0,
				"updateTime": "",
				"userId": 0
			}
		],
		"size": 0,
		"total": 0
	},
	"message": ""
}
```


## listSpaceVOByPage


**接口地址**:`/api/space/list/page/vo`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "current": 0,
  "id": 0,
  "pageSize": 0,
  "sortField": "",
  "sortOrder": "",
  "spaceLevel": 0,
  "spaceName": "",
  "spaceType": 0,
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceQueryRequest|spaceQueryRequest|body|true|SpaceQueryRequest|SpaceQueryRequest|
|&emsp;&emsp;current|||false|integer(int32)||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;sortField|||false|string||
|&emsp;&emsp;sortOrder|||false|string||
|&emsp;&emsp;spaceLevel|||false|integer(int32)||
|&emsp;&emsp;spaceName|||false|string||
|&emsp;&emsp;spaceType|||false|integer(int32)||
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Page«SpaceVO»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Page«SpaceVO»|Page«SpaceVO»|
|&emsp;&emsp;current||integer(int64)||
|&emsp;&emsp;pages||integer(int64)||
|&emsp;&emsp;records||array|SpaceVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;editTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;maxCount||integer||
|&emsp;&emsp;&emsp;&emsp;maxSize||integer||
|&emsp;&emsp;&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;&emsp;&emsp;spaceLevel||integer||
|&emsp;&emsp;&emsp;&emsp;spaceName||string||
|&emsp;&emsp;&emsp;&emsp;spaceType||integer||
|&emsp;&emsp;&emsp;&emsp;totalCount||integer||
|&emsp;&emsp;&emsp;&emsp;totalSize||integer||
|&emsp;&emsp;&emsp;&emsp;updateTime||string||
|&emsp;&emsp;&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;&emsp;&emsp;userId||integer||
|&emsp;&emsp;size||integer(int64)||
|&emsp;&emsp;total||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"current": 0,
		"pages": 0,
		"records": [
			{
				"createTime": "",
				"editTime": "",
				"id": 0,
				"maxCount": 0,
				"maxSize": 0,
				"permissionList": [],
				"spaceLevel": 0,
				"spaceName": "",
				"spaceType": 0,
				"totalCount": 0,
				"totalSize": 0,
				"updateTime": "",
				"user": {
					"createTime": "",
					"id": 0,
					"userAccount": "",
					"userAvatar": "",
					"userName": "",
					"userProfile": "",
					"userRole": "",
					"vipCode": "",
					"vipExpireTime": "",
					"vipNumber": 0
				},
				"userId": 0
			}
		],
		"size": 0,
		"total": 0
	},
	"message": ""
}
```


## updateSpace


**接口地址**:`/api/space/update`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "maxCount": 0,
  "maxSize": 0,
  "spaceLevel": 0,
  "spaceName": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceUpdateRequest|spaceUpdateRequest|body|true|SpaceUpdateRequest|SpaceUpdateRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;maxCount|||false|integer(int64)||
|&emsp;&emsp;maxSize|||false|integer(int64)||
|&emsp;&emsp;spaceLevel|||false|integer(int32)||
|&emsp;&emsp;spaceName|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


# space-user-controller


## addSpaceUser


**接口地址**:`/api/spaceUser/add`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "spaceId": 0,
  "spaceRole": "",
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceUserAddRequest|spaceUserAddRequest|body|true|SpaceUserAddRequest|SpaceUserAddRequest|
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;spaceRole|||false|string||
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«long»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||integer(int64)|integer(int64)|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": 0,
	"message": ""
}
```


## deleteSpaceUser


**接口地址**:`/api/spaceUser/delete`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|deleteRequest|deleteRequest|body|true|DeleteRequest|DeleteRequest|
|&emsp;&emsp;id|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## editSpaceUser


**接口地址**:`/api/spaceUser/edit`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "spaceRole": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceUserEditRequest|spaceUserEditRequest|body|true|SpaceUserEditRequest|SpaceUserEditRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;spaceRole|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## getSpaceUser


**接口地址**:`/api/spaceUser/get`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "spaceId": 0,
  "spaceRole": "",
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceUserQueryRequest|spaceUserQueryRequest|body|true|SpaceUserQueryRequest|SpaceUserQueryRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;spaceRole|||false|string||
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«SpaceUser»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||SpaceUser|SpaceUser|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;spaceRole||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"createTime": "",
		"id": 0,
		"spaceId": 0,
		"spaceRole": "",
		"updateTime": "",
		"userId": 0
	},
	"message": ""
}
```


## listSpaceUser


**接口地址**:`/api/spaceUser/list`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "spaceId": 0,
  "spaceRole": "",
  "userId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|spaceUserQueryRequest|spaceUserQueryRequest|body|true|SpaceUserQueryRequest|SpaceUserQueryRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;spaceId|||false|integer(int64)||
|&emsp;&emsp;spaceRole|||false|string||
|&emsp;&emsp;userId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«SpaceUserVO»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|SpaceUserVO|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;space||SpaceVO|SpaceVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;editTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;maxCount||integer||
|&emsp;&emsp;&emsp;&emsp;maxSize||integer||
|&emsp;&emsp;&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;&emsp;&emsp;spaceLevel||integer||
|&emsp;&emsp;&emsp;&emsp;spaceName||string||
|&emsp;&emsp;&emsp;&emsp;spaceType||integer||
|&emsp;&emsp;&emsp;&emsp;totalCount||integer||
|&emsp;&emsp;&emsp;&emsp;totalSize||integer||
|&emsp;&emsp;&emsp;&emsp;updateTime||string||
|&emsp;&emsp;&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;&emsp;&emsp;userId||integer||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;spaceRole||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"createTime": "",
			"id": 0,
			"space": {
				"createTime": "",
				"editTime": "",
				"id": 0,
				"maxCount": 0,
				"maxSize": 0,
				"permissionList": [],
				"spaceLevel": 0,
				"spaceName": "",
				"spaceType": 0,
				"totalCount": 0,
				"totalSize": 0,
				"updateTime": "",
				"user": {
					"createTime": "",
					"id": 0,
					"userAccount": "",
					"userAvatar": "",
					"userName": "",
					"userProfile": "",
					"userRole": "",
					"vipCode": "",
					"vipExpireTime": "",
					"vipNumber": 0
				},
				"userId": 0
			},
			"spaceId": 0,
			"spaceRole": "",
			"updateTime": "",
			"user": {
				"createTime": "",
				"id": 0,
				"userAccount": "",
				"userAvatar": "",
				"userName": "",
				"userProfile": "",
				"userRole": "",
				"vipCode": "",
				"vipExpireTime": "",
				"vipNumber": 0
			},
			"userId": 0
		}
	],
	"message": ""
}
```


## listMyTeamSpace


**接口地址**:`/api/spaceUser/list/my`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«List«SpaceUserVO»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||array|SpaceUserVO|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;space||SpaceVO|SpaceVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;editTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;maxCount||integer||
|&emsp;&emsp;&emsp;&emsp;maxSize||integer||
|&emsp;&emsp;&emsp;&emsp;permissionList||array|string|
|&emsp;&emsp;&emsp;&emsp;spaceLevel||integer||
|&emsp;&emsp;&emsp;&emsp;spaceName||string||
|&emsp;&emsp;&emsp;&emsp;spaceType||integer||
|&emsp;&emsp;&emsp;&emsp;totalCount||integer||
|&emsp;&emsp;&emsp;&emsp;totalSize||integer||
|&emsp;&emsp;&emsp;&emsp;updateTime||string||
|&emsp;&emsp;&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;&emsp;&emsp;userId||integer||
|&emsp;&emsp;spaceId||integer(int64)||
|&emsp;&emsp;spaceRole||string||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;user||UserVO|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;userId||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": [
		{
			"createTime": "",
			"id": 0,
			"space": {
				"createTime": "",
				"editTime": "",
				"id": 0,
				"maxCount": 0,
				"maxSize": 0,
				"permissionList": [],
				"spaceLevel": 0,
				"spaceName": "",
				"spaceType": 0,
				"totalCount": 0,
				"totalSize": 0,
				"updateTime": "",
				"user": {
					"createTime": "",
					"id": 0,
					"userAccount": "",
					"userAvatar": "",
					"userName": "",
					"userProfile": "",
					"userRole": "",
					"vipCode": "",
					"vipExpireTime": "",
					"vipNumber": 0
				},
				"userId": 0
			},
			"spaceId": 0,
			"spaceRole": "",
			"updateTime": "",
			"user": {
				"createTime": "",
				"id": 0,
				"userAccount": "",
				"userAvatar": "",
				"userName": "",
				"userProfile": "",
				"userRole": "",
				"vipCode": "",
				"vipExpireTime": "",
				"vipNumber": 0
			},
			"userId": 0
		}
	],
	"message": ""
}
```


# user-controller


## addUser


**接口地址**:`/api/user/add`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "userAccount": "",
  "userAvatar": "",
  "userName": "",
  "userProfile": "",
  "userRole": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userAddRequest|userAddRequest|body|true|UserAddRequest|UserAddRequest|
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userAvatar|||false|string||
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«long»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||integer(int64)|integer(int64)|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": 0,
	"message": ""
}
```


## deleteUser


**接口地址**:`/api/user/delete`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|deleteRequest|deleteRequest|body|true|DeleteRequest|DeleteRequest|
|&emsp;&emsp;id|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## exchangeVip


**接口地址**:`/api/user/exchange/vip`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "vipCode": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|vipExchangeRequest|vipExchangeRequest|body|true|VipExchangeRequest|VipExchangeRequest|
|&emsp;&emsp;vipCode|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## getUserById


**接口地址**:`/api/user/get`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|id|query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«User»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||User|User|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;isDelete||integer(int32)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userPassword||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|&emsp;&emsp;vipCode||string||
|&emsp;&emsp;vipExpireTime||string(date-time)||
|&emsp;&emsp;vipNumber||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"createTime": "",
		"editTime": "",
		"id": 0,
		"isDelete": 0,
		"updateTime": "",
		"userAccount": "",
		"userAvatar": "",
		"userName": "",
		"userPassword": "",
		"userProfile": "",
		"userRole": "",
		"vipCode": "",
		"vipExpireTime": "",
		"vipNumber": 0
	},
	"message": ""
}
```


## getLoginUser


**接口地址**:`/api/user/get/login`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«LoginUserVO»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||LoginUserVO|LoginUserVO|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"createTime": "",
		"editTime": "",
		"id": 0,
		"updateTime": "",
		"userAccount": "",
		"userAvatar": "",
		"userName": "",
		"userProfile": "",
		"userRole": ""
	},
	"message": ""
}
```


## getUserVOById


**接口地址**:`/api/user/get/vo`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|id|query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«UserVO»|
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||UserVO|UserVO|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|&emsp;&emsp;vipCode||string||
|&emsp;&emsp;vipExpireTime||string(date-time)||
|&emsp;&emsp;vipNumber||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"createTime": "",
		"id": 0,
		"userAccount": "",
		"userAvatar": "",
		"userName": "",
		"userProfile": "",
		"userRole": "",
		"vipCode": "",
		"vipExpireTime": "",
		"vipNumber": 0
	},
	"message": ""
}
```


## listUserVOByPage


**接口地址**:`/api/user/list/page/vo`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "current": 0,
  "id": 0,
  "pageSize": 0,
  "sortField": "",
  "sortOrder": "",
  "userAccount": "",
  "userName": "",
  "userProfile": "",
  "userRole": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userQueryRequest|userQueryRequest|body|true|UserQueryRequest|UserQueryRequest|
|&emsp;&emsp;current|||false|integer(int32)||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;sortField|||false|string||
|&emsp;&emsp;sortOrder|||false|string||
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«Page«UserVO»»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||Page«UserVO»|Page«UserVO»|
|&emsp;&emsp;current||integer(int64)||
|&emsp;&emsp;pages||integer(int64)||
|&emsp;&emsp;records||array|UserVO|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;vipCode||string||
|&emsp;&emsp;&emsp;&emsp;vipExpireTime||string||
|&emsp;&emsp;&emsp;&emsp;vipNumber||integer||
|&emsp;&emsp;size||integer(int64)||
|&emsp;&emsp;total||integer(int64)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"current": 0,
		"pages": 0,
		"records": [
			{
				"createTime": "",
				"id": 0,
				"userAccount": "",
				"userAvatar": "",
				"userName": "",
				"userProfile": "",
				"userRole": "",
				"vipCode": "",
				"vipExpireTime": "",
				"vipNumber": 0
			}
		],
		"size": 0,
		"total": 0
	},
	"message": ""
}
```


## userLogin


**接口地址**:`/api/user/login`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "userAccount": "",
  "userPassword": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userLoginRequest|userLoginRequest|body|true|UserLoginRequest|UserLoginRequest|
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userPassword|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«LoginUserVO»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||LoginUserVO|LoginUserVO|
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"createTime": "",
		"editTime": "",
		"id": 0,
		"updateTime": "",
		"userAccount": "",
		"userAvatar": "",
		"userName": "",
		"userProfile": "",
		"userRole": ""
	},
	"message": ""
}
```


## userLogout


**接口地址**:`/api/user/logout`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## userRegister


**接口地址**:`/api/user/register`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "checkPassword": "",
  "userAccount": "",
  "userPassword": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userRegisterRequest|userRegisterRequest|body|true|UserRegisterRequest|UserRegisterRequest|
|&emsp;&emsp;checkPassword|||false|string||
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userPassword|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«long»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||integer(int64)|integer(int64)|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": 0,
	"message": ""
}
```


## updateUser


**接口地址**:`/api/user/update`


**请求方式**:`POST`


**请求数据类型**:`application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "userAvatar": "",
  "userName": "",
  "userProfile": "",
  "userRole": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userUpdateRequest|userUpdateRequest|body|true|UserUpdateRequest|UserUpdateRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;userAvatar|||false|string||
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponse«boolean»|
|201|Created||
|401|Unauthorized||
|403|Forbidden||
|404|Not Found||


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```