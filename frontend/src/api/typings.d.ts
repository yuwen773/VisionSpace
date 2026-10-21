declare namespace API {
  type AdminDashboardStatsVO = {
    userCount?: number
    pictureCount?: number
    spaceCount?: number
    usedSize?: number
    todayImpressionCount?: number
    todayClickCount?: number
    todayCtr?: number
    todayCollectCount?: number
  }

  type AgentChatRequest = {
    message?: string
    threadId?: string
    userId?: string
    imageUrls?: string[]
    enabledMcpServers?: string[]
    disableMcpDefault?: boolean
  }

  type BaseResponseAdminDashboardStatsVO = {
    code?: number
    data?: AdminDashboardStatsVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseChatHistoryVO = {
    code?: number
    data?: ChatHistoryVO
    message?: string
  }

  type BaseResponseCreateOutPaintingTaskResponse = {
    code?: number
    data?: CreateOutPaintingTaskResponse
    message?: string
  }

  type BaseResponseFeedbackVO = {
    code?: number
    data?: FeedbackVO
    message?: string
  }

  type BaseResponseGetOutPaintingTaskResponse = {
    code?: number
    data?: GetOutPaintingTaskResponse
    message?: string
  }

  type BaseResponseInteger = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseIPageMcpServerDetail = {
    code?: number
    data?: IPageMcpServerDetail
    message?: string
  }

  type BaseResponseListImageSearchResult = {
    code?: number
    data?: ImageSearchResult[]
    message?: string
  }

  type BaseResponseListPictureVO = {
    code?: number
    data?: PictureVO[]
    message?: string
  }

  type BaseResponseListSessionVO = {
    code?: number
    data?: SessionVO[]
    message?: string
  }

  type BaseResponseListSpace = {
    code?: number
    data?: Space[]
    message?: string
  }

  type BaseResponseListSpaceCategoryAnalyzeResponse = {
    code?: number
    data?: SpaceCategoryAnalyzeResponse[]
    message?: string
  }

  type BaseResponseListSpaceLevel = {
    code?: number
    data?: SpaceLevel[]
    message?: string
  }

  type BaseResponseListSpaceSizeAnalyzeResponse = {
    code?: number
    data?: SpaceSizeAnalyzeResponse[]
    message?: string
  }

  type BaseResponseListSpaceTagAnalyzeResponse = {
    code?: number
    data?: SpaceTagAnalyzeResponse[]
    message?: string
  }

  type BaseResponseListSpaceUserAnalyzeResponse = {
    code?: number
    data?: SpaceUserAnalyzeResponse[]
    message?: string
  }

  type BaseResponseListSpaceUserVO = {
    code?: number
    data?: SpaceUserVO[]
    message?: string
  }

  type BaseResponseListStorageConfigVO = {
    code?: number
    data?: StorageConfigVO[]
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseMcpServerDetail = {
    code?: number
    data?: McpServerDetail
    message?: string
  }

  type BaseResponsePageFeedbackVO = {
    code?: number
    data?: PageFeedbackVO
    message?: string
  }

  type BaseResponsePagePicture = {
    code?: number
    data?: PagePicture
    message?: string
  }

  type BaseResponsePagePictureVO = {
    code?: number
    data?: PagePictureVO
    message?: string
  }

  type BaseResponsePageSpace = {
    code?: number
    data?: PageSpace
    message?: string
  }

  type BaseResponsePageSpaceVO = {
    code?: number
    data?: PageSpaceVO
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponsePicture = {
    code?: number
    data?: Picture
    message?: string
  }

  type BaseResponsePictureTagCategory = {
    code?: number
    data?: PictureTagCategory
    message?: string
  }

  type BaseResponsePictureVO = {
    code?: number
    data?: PictureVO
    message?: string
  }

  type BaseResponseRecommendPageVO = {
    code?: number
    data?: RecommendPageVO
    message?: string
  }

  type BaseResponseSpace = {
    code?: number
    data?: Space
    message?: string
  }

  type BaseResponseSpaceUsageAnalyzeResponse = {
    code?: number
    data?: SpaceUsageAnalyzeResponse
    message?: string
  }

  type BaseResponseSpaceUser = {
    code?: number
    data?: SpaceUser
    message?: string
  }

  type BaseResponseSpaceVO = {
    code?: number
    data?: SpaceVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserPictureStatsResponse = {
    code?: number
    data?: UserPictureStatsResponse
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type BaseResponseVoid = {
    code?: number
    data?: any
    message?: string
  }

  type ChatHistoryVO = {
    messages?: MessageVO[]
    hasMore?: boolean
    nextCursor?: number
  }

  type CreateOutPaintingTaskResponse = {
    output?: Output
    code?: string
    message?: string
    requestId?: string
  }

  type CreatePictureOutPaintingTaskRequest = {
    pictureId?: number
    parameters?: Parameters
  }

  type deleteMcpServerUsingDeleteParams = {
    mcpServerCode: string
  }

  type DeleteRequest = {
    id?: number
  }

  type FeedbackAddRequest = {
    type?: number
    title?: string
    content?: string
    pictureUrls?: string[]
  }

  type FeedbackQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    type?: number
    status?: number
  }

  type FeedbackRequest = {
    threadId?: string
    userId?: string
    satisfied?: boolean
    reason?: string
    action?: 'RETURN' | 'REGENERATE' | 'RESEARCH'
    currentPhase?: 'EXPLORATION' | 'GENERATION' | 'DONE'
    message?: string
  }

  type FeedbackUpdateStatusRequest = {
    id?: number
    status?: number
    handlerNote?: string
  }

  type FeedbackVO = {
    id?: number
    userId?: number
    userVO?: UserVO
    type?: number
    title?: string
    content?: string
    pictureUrls?: string[]
    status?: number
    handlerId?: number
    handlerVO?: UserVO
    handlerNote?: string
    handleTime?: string
    createTime?: string
  }

  type getFeedbackByIdUsingGetParams = {
    id: number
  }

  type getHistoryUsingGetParams = {
    threadId: string
    beforeId?: number
    limit?: number
  }

  type getMcpServerUsingGetParams = {
    mcpServerCode: string
    needTools?: boolean
  }

  type GetOutPaintingTaskResponse = {
    requestId?: string
    output?: Output1
  }

  type getPictureByIdUsingGetParams = {
    id: number
  }

  type getPictureOutPaintingTaskUsingGetParams = {
    taskId: string
  }

  type getPictureVOByIdUsingGetParams = {
    id: number
  }

  type getRecommendListUsingGetParams = {
    type?: string
    page?: number
    size?: number
  }

  type getSessionsUsingGetParams = {
    beforeId?: number
    limit?: number
  }

  type getSpaceByIdUsingGetParams = {
    id: number
  }

  type getSpaceVOByIdUsingGetParams = {
    id: number
  }

  type getUserByIdUsingGetParams = {
    id: number
  }

  type getUserRecentPicturesUsingGetParams = {
    count?: number
  }

  type getUserVOByIdUsingGetParams = {
    id: number
  }

  type ImageSearchResult = {
    thumbUrl?: string
    fromUrl?: string
  }

  type InputSchema = {
    type?: string
    properties?: Record<string, any>
    required?: string[]
  }

  type IPageMcpServerDetail = {
    size?: number
    current?: number
    total?: number
    records?: McpServerDetail[]
    pages?: number
  }

  type listMcpServersUsingGetParams = {
    name?: string
    current?: number
    size?: number
  }

  type listStorageConfigUsingGetParams = {
    queryRequest: StorageConfigQueryRequest
  }

  type LoginUserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
  }

  type McpServerDetail = {
    mcpServerCode?: string
    name?: string
    description?: string
    deployConfig?: string
    status?: number
    installType?: string
    host?: string
    tools?: McpTool[]
  }

  type McpTool = {
    name?: string
    description?: string
    inputSchema?: InputSchema
  }

  type MessageVO = {
    id?: number
    role?: string
    subType?: string
    content?: string
    createdTime?: string
    summary?: boolean
  }

  type OrderItem = {
    column?: string
    asc?: boolean
  }

  type Output = {
    taskId?: string
    taskStatus?: string
  }

  type PageFeedbackVO = {
    records?: FeedbackVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageFeedbackVO
    searchCount?: PageFeedbackVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PagePicture = {
    records?: Picture[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PagePicture
    searchCount?: PagePicture
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PagePictureVO = {
    records?: PictureVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PagePictureVO
    searchCount?: PagePictureVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageSpace = {
    records?: Space[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageSpace
    searchCount?: PageSpace
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageSpaceVO = {
    records?: SpaceVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageSpaceVO
    searchCount?: PageSpaceVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageUserVO = {
    records?: UserVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageUserVO
    searchCount?: PageUserVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type Parameters = {
    angle?: number
    outputRatio?: string
    topOffset?: number
    bottomOffset?: number
    leftOffset?: number
    rightOffset?: number
    bestQuality?: boolean
    limitImageSize?: boolean
    addWatermark?: boolean
    xScale?: number
    yScale?: number
  }

  type Picture = {
    id?: number
    url?: string
    thumbnailUrl?: string
    previewUrl?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string
    picSize?: number
    picWidth?: number
    picHeight?: number
    picScale?: number
    picFormat?: string
    picColor?: string
    userId?: number
    spaceId?: number
    reviewStatus?: number
    reviewMessage?: string
    reviewerId?: number
    reviewTime?: string
    createTime?: string
    editTime?: string
    updateTime?: string
    isDelete?: number
  }

  type PictureActionRequest = {
    pictureId?: number
    actionType?: number
    source?: number
  }

  type PictureEditByBatchRequest = {
    pictureIdList?: number[]
    spaceId?: number
    category?: string
    tags?: string[]
    nameRule?: string
  }

  type PictureEditRequest = {
    id?: number
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
  }

  type PictureQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
    picSize?: number
    picWidth?: number
    picHeight?: number
    picScale?: number
    picFormat?: string
    searchText?: string
    userId?: number
    reviewStatus?: number
    reviewMessage?: string
    reviewerId?: number
    reviewTime?: string
    spaceId?: number
    nullSpaceId?: boolean
    startEditTime?: string
    endEditTime?: string
  }

  type PictureReviewRequest = {
    id?: number
    reviewStatus?: number
    reviewMessage?: string
  }

  type PictureTagCategory = {
    tagList?: string[]
    categoryList?: string[]
  }

  type PictureUpdateRequest = {
    id?: number
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
  }

  type PictureUploadByBatchRequest = {
    searchText?: string
    count?: number
    namePrefix?: string
  }

  type PictureUploadRequest = {
    id?: number
    fileUrl?: string
    picName?: string
    spaceId?: number
  }

  type PictureVO = {
    id?: number
    url?: string
    thumbnailUrl?: string
    previewUrl?: string
    name?: string
    introduction?: string
    tags?: string[]
    category?: string
    picSize?: number
    picWidth?: number
    picHeight?: number
    picScale?: number
    picFormat?: string
    picColor?: string
    userId?: number
    spaceId?: number
    createTime?: string
    editTime?: string
    updateTime?: string
    user?: UserVO
    permissionList?: string[]
    viewCount?: number
    likeCount?: number
    collectCount?: number
    downloadCount?: number
    shareCount?: number
    impressionCount?: number
    clickCount?: number
  }

  type RecommendPageVO = {
    records?: RecommendPictureVO[]
    total?: number
  }

  type RecommendPictureVO = {
    id?: number
    thumbnailUrl?: string
    name?: string
    picWidth?: number
    picHeight?: number
    picScale?: number
    picColor?: string
    tags?: string[]
  }

  type SearchPictureByColorRequest = {
    picColor?: string
    spaceId?: number
  }

  type SearchPictureByPictureRequest = {
    pictureId?: number
  }

  type ServerSentEventString = Record<string, any>

  type SessionVO = {
    sessionId?: string
    title?: string
    lastMessage?: string
    messageCount?: number
    summaryContent?: string
    createdTime?: string
    updatedTime?: string
  }

  type Space = {
    id?: number
    spaceName?: string
    spaceLevel?: number
    spaceType?: number
    maxSize?: number
    maxCount?: number
    totalSize?: number
    totalCount?: number
    userId?: number
    createTime?: string
    editTime?: string
    updateTime?: string
    isDelete?: number
  }

  type SpaceAddRequest = {
    spaceName?: string
    spaceLevel?: number
    spaceType?: number
  }

  type SpaceCategoryAnalyzeRequest = {
    spaceId?: number
    queryPublic?: boolean
    queryAll?: boolean
  }

  type SpaceCategoryAnalyzeResponse = {
    category?: string
    count?: number
    totalSize?: number
  }

  type SpaceEditRequest = {
    id?: number
    spaceName?: string
  }

  type SpaceLevel = {
    value?: number
    text?: string
    maxCount?: number
    maxSize?: number
  }

  type SpaceQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userId?: number
    spaceName?: string
    spaceLevel?: number
    spaceType?: number
  }

  type SpaceRankAnalyzeRequest = {
    topN?: number
  }

  type SpaceSizeAnalyzeRequest = {
    spaceId?: number
    queryPublic?: boolean
    queryAll?: boolean
  }

  type SpaceSizeAnalyzeResponse = {
    sizeRange?: string
    count?: number
  }

  type SpaceTagAnalyzeRequest = {
    spaceId?: number
    queryPublic?: boolean
    queryAll?: boolean
  }

  type SpaceTagAnalyzeResponse = {
    tag?: string
    count?: number
  }

  type SpaceUpdateRequest = {
    id?: number
    spaceName?: string
    spaceLevel?: number
    maxSize?: number
    maxCount?: number
  }

  type SpaceUsageAnalyzeRequest = {
    spaceId?: number
    queryPublic?: boolean
    queryAll?: boolean
  }

  type SpaceUsageAnalyzeResponse = {
    usedSize?: number
    maxSize?: number
    sizeUsageRatio?: number
    usedCount?: number
    maxCount?: number
    countUsageRatio?: number
  }

  type SpaceUser = {
    id?: number
    spaceId?: number
    userId?: number
    spaceRole?: string
    createTime?: string
    updateTime?: string
  }

  type SpaceUserAddRequest = {
    spaceId?: number
    userId?: number
    spaceRole?: string
  }

  type SpaceUserAnalyzeRequest = {
    spaceId?: number
    queryPublic?: boolean
    queryAll?: boolean
    userId?: number
    timeDimension?: string
  }

  type SpaceUserAnalyzeResponse = {
    period?: string
    count?: number
  }

  type SpaceUserEditRequest = {
    id?: number
    spaceRole?: string
  }

  type SpaceUserQueryRequest = {
    id?: number
    spaceId?: number
    userId?: number
    spaceRole?: string
  }

  type SpaceUserVO = {
    id?: number
    spaceId?: number
    userId?: number
    spaceRole?: string
    createTime?: string
    updateTime?: string
    user?: UserVO
    space?: SpaceVO
  }

  type SpaceVO = {
    id?: number
    spaceName?: string
    spaceLevel?: number
    spaceType?: number
    maxSize?: number
    maxCount?: number
    totalSize?: number
    totalCount?: number
    userId?: number
    createTime?: string
    editTime?: string
    updateTime?: string
    user?: UserVO
    permissionList?: string[]
  }

  type StorageConfigActiveRequest = {
    id?: number
  }

  type StorageConfigAddRequest = {
    platform?: string
    platformName?: string
    endpoint?: string
    region?: string
    bucket?: string
    accessKey?: string
    secretKey?: string
    domain?: string
    basePath?: string
    isActive?: number
    isDefault?: number
    orderNum?: number
    status?: number
  }

  type StorageConfigQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    platform?: string
    status?: number
  }

  type StorageConfigUpdateRequest = {
    id?: number
    platform?: string
    platformName?: string
    endpoint?: string
    region?: string
    bucket?: string
    accessKey?: string
    secretKey?: string
    domain?: string
    basePath?: string
    isActive?: number
    isDefault?: number
    orderNum?: number
    status?: number
  }

  type StorageConfigVO = {
    id?: number
    platform?: string
    platformName?: string
    endpoint?: string
    region?: string
    bucket?: string
    accessKey?: string
    secretKey?: string
    domain?: string
    basePath?: string
    isActive?: number
    isDefault?: number
    orderNum?: number
    status?: number
    createTime?: string
    updateTime?: string
  }

  type uploadPictureUsingPostParams = {
    pictureUploadRequest: PictureUploadRequest
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    vipExpireTime?: string
    vipCode?: string
    vipNumber?: number
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserPictureStatsResponse = {
    uploadCount?: number
    likeCount?: number
    reviewPassCount?: number
    reviewPassRate?: number
  }

  type UserQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    vipExpireTime?: string
    vipCode?: string
    vipNumber?: number
    createTime?: string
  }

  type VipExchangeRequest = {
    vipCode?: string
  }

  // ===== 兼容层：旧版生成器（尾下划线命名）的别名 =====
  type BaseResponseAdminDashboardStatsVO_ = BaseResponseAdminDashboardStatsVO
  type BaseResponseBoolean_ = BaseResponseBoolean
  type BaseResponseCreateOutPaintingTaskResponse_ = BaseResponseCreateOutPaintingTaskResponse
  type BaseResponseFeedbackVO_ = BaseResponseFeedbackVO
  type BaseResponseGetOutPaintingTaskResponse_ = BaseResponseGetOutPaintingTaskResponse
  type BaseResponseInt_ = BaseResponseInteger
  type BaseResponseListImageSearchResult_ = BaseResponseListImageSearchResult
  type BaseResponseListPictureVO_ = BaseResponseListPictureVO
  type BaseResponseListSpaceCategoryAnalyzeResponse_ = BaseResponseListSpaceCategoryAnalyzeResponse
  type BaseResponseListSpaceLevel_ = BaseResponseListSpaceLevel
  type BaseResponseListSpaceSizeAnalyzeResponse_ = BaseResponseListSpaceSizeAnalyzeResponse
  type BaseResponseListSpaceTagAnalyzeResponse_ = BaseResponseListSpaceTagAnalyzeResponse
  type BaseResponseListSpaceUserAnalyzeResponse_ = BaseResponseListSpaceUserAnalyzeResponse
  type BaseResponseListSpaceUserVO_ = BaseResponseListSpaceUserVO
  type BaseResponseListSpace_ = BaseResponseListSpace
  type BaseResponseLoginUserVO_ = BaseResponseLoginUserVO
  type BaseResponseLong_ = BaseResponseLong
  type BaseResponsePageFeedbackVO_ = BaseResponsePageFeedbackVO
  type BaseResponsePagePictureVO_ = BaseResponsePagePictureVO
  type BaseResponsePagePicture_ = BaseResponsePagePicture
  type BaseResponsePageSpaceVO_ = BaseResponsePageSpaceVO
  type BaseResponsePageSpace_ = BaseResponsePageSpace
  type BaseResponsePageUserVO_ = BaseResponsePageUserVO
  type BaseResponsePictureTagCategory_ = BaseResponsePictureTagCategory
  type BaseResponsePictureVO_ = BaseResponsePictureVO
  type BaseResponsePicture_ = BaseResponsePicture
  type BaseResponseSpaceUsageAnalyzeResponse_ = BaseResponseSpaceUsageAnalyzeResponse
  type BaseResponseSpaceUser_ = BaseResponseSpaceUser
  type BaseResponseSpaceVO_ = BaseResponseSpaceVO
  type BaseResponseSpace_ = BaseResponseSpace
  type BaseResponseString_ = BaseResponseString
  type BaseResponseUserVO_ = BaseResponseUserVO
  type BaseResponseUser_ = BaseResponseUser
  type PageFeedbackVO_ = PageFeedbackVO
  type PagePictureVO_ = PagePictureVO
  type PagePicture_ = PagePicture
  type PageSpaceVO_ = PageSpaceVO
  type PageSpace_ = PageSpace
  type PageUserVO_ = PageUserVO
  type getPictureByIdUsingGETParams = getPictureByIdUsingGetParams
  type getPictureOutPaintingTaskUsingGETParams = getPictureOutPaintingTaskUsingGetParams
  type getPictureVOByIdUsingGETParams = getPictureVOByIdUsingGetParams
  type getSpaceByIdUsingGETParams = getSpaceByIdUsingGetParams
  type getSpaceVOByIdUsingGETParams = getSpaceVOByIdUsingGetParams
  type getUserByIdUsingGETParams = getUserByIdUsingGetParams
  type getUserVOByIdUsingGETParams = getUserVOByIdUsingGetParams
  type uploadPictureUsingPOSTParams = uploadPictureUsingPostParams

  type Output1 = {
    code?: string
    endTime?: string
    message?: string
    outputImageUrl?: string
    scheduledTime?: string
    submitTime?: string
    taskId?: string
    taskMetrics?: TaskMetrics
    taskStatus?: string
  }


  type TaskMetrics = {
    failed?: number
    succeeded?: number
    total?: number
  }


  type testDownloadFileUsingGETParams = {
    /** filepath */
    filepath?: string
  }

  // ===== 补充：旧产物缺失、后端实际存在的类型 =====
  type BaseResponseUserPictureStatsResponse_ = BaseResponseUserPictureStatsResponse
  type BaseResponseListRecentPictureVO_ = BaseResponseListPictureVO
}
