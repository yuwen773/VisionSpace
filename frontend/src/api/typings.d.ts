declare namespace API {
  type ActionReviewRequest = {
    actionId?: string
    note?: string
    threadId?: string
  }

  type AdminDashboardStatsVO = {
    pictureCount?: number
    spaceCount?: number
    todayClickCount?: number
    todayCollectCount?: number
    todayCtr?: number
    todayImpressionCount?: number
    usedSize?: number
    userCount?: number
  }

  type AgentChatRequest = {
    attachmentIds?: string[]
    disableMcpDefault?: boolean
    enabledMcpServers?: string[]
    imageUrls?: string[]
    message?: string
    threadId?: string
  }

  type AgentImageVO = {
    resourceId?: number
    size?: number
    url?: string
  }

  type BaseResponseAdminDashboardStatsVO = {
    code?: number
    data?: AdminDashboardStatsVO
    message?: string
  }

  type BaseResponseAgentImageVO = {
    code?: number
    data?: AgentImageVO
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

  type BaseResponseFeedbackAttachmentVO = {
    code?: number
    data?: FeedbackAttachmentVO
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

  type BaseResponseListModerationResourceVO = {
    code?: number
    data?: ModerationResourceVO[]
    message?: string
  }

  type BaseResponseListPictureVO = {
    code?: number
    data?: PictureVO[]
    message?: string
  }

  type BaseResponseListResourceGrantVO = {
    code?: number
    data?: ResourceGrantVO[]
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

  type BaseResponseMcpCapabilities = {
    code?: number
    data?: McpCapabilities
    message?: string
  }

  type BaseResponseMcpServerDetail = {
    code?: number
    data?: McpServerDetail
    message?: string
  }

  type BaseResponseModerationResourceVO = {
    code?: number
    data?: ModerationResourceVO
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

  type BaseResponsePictureActionReportMetricsVO = {
    code?: number
    data?: PictureActionReportMetricsVO
    message?: string
  }

  type BaseResponsePictureStatsBacklogVO = {
    code?: number
    data?: PictureStatsBacklogVO
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

  type BaseResponseRecoveryStateVO = {
    code?: number
    data?: RecoveryStateVO
    message?: string
  }

  type BaseResponseResourceGrantListVO = {
    code?: number
    data?: ResourceGrantListVO
    message?: string
  }

  type BaseResponseResourceGrantVO = {
    code?: number
    data?: ResourceGrantVO
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

  type BaseResponseVipExchangeResult = {
    code?: number
    data?: VipExchangeResult
    message?: string
  }

  type BaseResponseVoid = {
    code?: number
    data?: any
    message?: string
  }

  type ChatHistoryVO = {
    hasMore?: boolean
    messages?: MessageVO[]
    nextCursor?: number
  }

  type contentUsingGetParams = {
    resourceId: number
    sessionId: string
  }

  type CreateOutPaintingTaskOutput = {
    taskId?: string
    taskStatus?: string
  }

  type CreateOutPaintingTaskResponse = {
    code?: string
    message?: string
    output?: CreateOutPaintingTaskOutput
    requestId?: string
  }

  type CreatePictureOutPaintingTaskRequest = {
    parameters?: Parameters
    pictureId?: number
  }

  type deleteAttachmentUsingPostParams = {
    resourceId: number
  }

  type deleteMcpServerUsingDeleteParams = {
    mcpServerCode: string
  }

  type DeleteRequest = {
    id?: number
  }

  type deleteSessionUsingDeleteParams = {
    threadId: string
  }

  type FeedbackAddRequest = {
    attachmentIds?: string[]
    content?: string
    pictureUrls?: string[]
    title?: string
    type?: number
  }

  type FeedbackAttachmentVO = {
    id?: string
    status?: string
    url?: string
  }

  type FeedbackQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    status?: number
    type?: number
  }

  type FeedbackRequest = {
    action?: 'RETURN' | 'REGENERATE' | 'RESEARCH'
    currentPhase?: 'EXPLORATION' | 'GENERATION' | 'DONE'
    message?: string
    reason?: string
    satisfied?: boolean
    threadId?: string
  }

  type FeedbackUpdateStatusRequest = {
    handlerNote?: string
    id?: number
    status?: number
  }

  type FeedbackVO = {
    attachments?: FeedbackAttachmentVO[]
    content?: string
    createTime?: string
    handleTime?: string
    handlerId?: number
    handlerName?: string
    handlerNote?: string
    id?: number
    status?: number
    title?: string
    type?: number
    userId?: number
    userName?: string
  }

  type getAttachmentUsingGetParams = {
    id: number
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

  type GetOutPaintingTaskOutput = {
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

  type GetOutPaintingTaskResponse = {
    output?: GetOutPaintingTaskOutput
    requestId?: string
  }

  type getPictureByIdUsingGetParams = {
    id: number
  }

  type getPictureOutPaintingTaskUsingGetParams = {
    taskId: string
  }

  type getPictureVoByIdUsingGetParams = {
    id: number
  }

  type getRecommendListUsingGetParams = {
    type?: string
    page?: number
    size?: number
    context?: string
  }

  type getRecoveryStateUsingGetParams = {
    threadId: string
  }

  type getSessionsUsingGetParams = {
    beforeId?: number
    limit?: number
  }

  type getSpaceByIdUsingGetParams = {
    id: number
  }

  type getSpaceVoByIdUsingGetParams = {
    id: number
  }

  type getUserByIdUsingGetParams = {
    id: number
  }

  type getUserRecentPicturesUsingGetParams = {
    count?: number
  }

  type getUserVoByIdUsingGetParams = {
    id: number
  }

  type historyUsingGetParams = {
    resourceType: string
    resourceId: string
  }

  type ImageSearchResult = {
    fromUrl?: string
    thumbUrl?: string
  }

  type InputSchema = {
    properties?: Record<string, any>
    required?: string[]
    type?: string
  }

  type IPageMcpServerDetail = {
    current?: number
    pages?: number
    records?: McpServerDetail[]
    size?: number
    total?: number
  }

  type listGrantsUsingGetParams = {
    pictureId: string
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
    createTime?: string
    editTime?: string
    id?: number
    updateTime?: string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
    vipExpireTime?: string
  }

  type McpCapabilities = {
    stdioDisabled?: boolean
  }

  type McpServerDetail = {
    deployConfig?: string
    description?: string
    host?: string
    installType?: string
    mcpServerCode?: string
    name?: string
    status?: number
    tools?: McpTool[]
  }

  type McpTool = {
    description?: string
    inputSchema?: InputSchema
    name?: string
  }

  type MessageVO = {
    attachmentIds?: string[]
    content?: string
    createdTime?: string
    id?: number
    role?: string
    subType?: string
    summary?: boolean
  }

  type ModerationResourceVO = {
    createTime?: string
    history?: ModerationResourceVO[]
    operatorId?: string
    operatorUserName?: string
    reason?: string
    releaseOperatorId?: string
    releaseOperatorUserName?: string
    releaseReason?: string
    releaseTime?: string
    resourceId?: string
    resourceName?: string
    resourceType?: string
    status?: string
  }

  type OrderItem = {
    asc?: boolean
    column?: string
  }

  type originalUsingGetParams = {
    pictureId: number
    download?: boolean
  }

  type PageFeedbackVO = {
    countId?: string
    current?: number
    maxLimit?: number
    optimizeCountSql?: PageFeedbackVO
    optimizeJoinOfCountSql?: boolean
    orders?: OrderItem[]
    pages?: number
    records?: FeedbackVO[]
    searchCount?: PageFeedbackVO
    size?: number
    total?: number
  }

  type PagePicture = {
    countId?: string
    current?: number
    maxLimit?: number
    optimizeCountSql?: PagePicture
    optimizeJoinOfCountSql?: boolean
    orders?: OrderItem[]
    pages?: number
    records?: Picture[]
    searchCount?: PagePicture
    size?: number
    total?: number
  }

  type PagePictureVO = {
    countId?: string
    current?: number
    maxLimit?: number
    optimizeCountSql?: PagePictureVO
    optimizeJoinOfCountSql?: boolean
    orders?: OrderItem[]
    pages?: number
    records?: PictureVO[]
    searchCount?: PagePictureVO
    size?: number
    total?: number
  }

  type PageSpace = {
    countId?: string
    current?: number
    maxLimit?: number
    optimizeCountSql?: PageSpace
    optimizeJoinOfCountSql?: boolean
    orders?: OrderItem[]
    pages?: number
    records?: Space[]
    searchCount?: PageSpace
    size?: number
    total?: number
  }

  type PageSpaceVO = {
    countId?: string
    current?: number
    maxLimit?: number
    optimizeCountSql?: PageSpaceVO
    optimizeJoinOfCountSql?: boolean
    orders?: OrderItem[]
    pages?: number
    records?: SpaceVO[]
    searchCount?: PageSpaceVO
    size?: number
    total?: number
  }

  type PageUserVO = {
    countId?: string
    current?: number
    maxLimit?: number
    optimizeCountSql?: PageUserVO
    optimizeJoinOfCountSql?: boolean
    orders?: OrderItem[]
    pages?: number
    records?: UserVO[]
    searchCount?: PageUserVO
    size?: number
    total?: number
  }

  type Parameters = {
    addWatermark?: boolean
    angle?: number
    bestQuality?: boolean
    bottomOffset?: number
    leftOffset?: number
    limitImageSize?: boolean
    outputRatio?: string
    rightOffset?: number
    topOffset?: number
    xScale?: number
    yScale?: number
  }

  type Picture = {
    category?: string
    createTime?: string
    editTime?: string
    id?: number
    introduction?: string
    isDelete?: number
    name?: string
    picColor?: string
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    previewUrl?: string
    reviewMessage?: string
    reviewStatus?: number
    reviewTime?: string
    reviewerId?: number
    spaceId?: number
    tags?: string
    thumbnailUrl?: string
    updateTime?: string
    url?: string
    userId?: number
  }

  type PictureActionReportMetricsVO = {
    acceptedCount?: number
    duplicateCount?: number
    rateLimitedCount?: number
    rejectedCount?: number
  }

  type PictureActionRequest = {
    actionType?: number
    eventId?: string
    pictureId?: number
    source?: number
    userId?: number
  }

  type PictureEditByBatchRequest = {
    category?: string
    nameRule?: string
    pictureIdList?: number[]
    spaceId?: number
    tags?: string[]
  }

  type PictureEditRequest = {
    category?: string
    id?: number
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureGrantCreateRequest = {
    expiresTime?: string
    granteeUserId?: string
    pictureId?: string
    reason?: string
  }

  type PictureGrantRevokeRequest = {
    grantId?: string
    reason?: string
  }

  type PictureQueryRequest = {
    category?: string
    current?: number
    endEditTime?: string
    id?: number
    introduction?: string
    name?: string
    nullSpaceId?: boolean
    pageSize?: number
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    reviewMessage?: string
    reviewStatus?: number
    reviewTime?: string
    reviewerId?: number
    searchText?: string
    sortField?: string
    sortOrder?: string
    spaceId?: number
    startEditTime?: string
    tags?: string[]
    userId?: number
  }

  type PictureReviewRequest = {
    id?: number
    reviewMessage?: string
    reviewStatus?: number
  }

  type PictureStatsBacklogVO = {
    accumulatedPendingCount?: number
    claimedCount?: number
    orphanCount?: number
    pendingCount?: number
    stuckCount?: number
  }

  type PictureTagCategory = {
    categoryList?: string[]
    tagList?: string[]
  }

  type PictureUpdateRequest = {
    category?: string
    id?: number
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureUploadByBatchRequest = {
    count?: number
    namePrefix?: string
    searchText?: string
  }

  type PictureUploadRequest = {
    fileUrl?: string
    id?: number
    picName?: string
    spaceId?: number
  }

  type PictureVO = {
    category?: string
    clickCount?: number
    collectCount?: number
    createTime?: string
    downloadCount?: number
    editTime?: string
    id?: number
    impressionCount?: number
    introduction?: string
    likeCount?: number
    name?: string
    permissionList?: string[]
    picColor?: string
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    previewUrl?: string
    quarantined?: boolean
    shareCount?: number
    spaceId?: number
    tags?: string[]
    thumbnailUrl?: string
    updateTime?: string
    url?: string
    user?: UserVO
    userId?: number
    viewCount?: number
  }

  type previewUsingGetParams = {
    pictureId: number
  }

  type RecommendPageVO = {
    context?: string
    contextValid?: boolean
    records?: RecommendPictureVO[]
    /** 总数：十进制字符串，避免 JavaScript 数字精度丢失 */
    total?: string
  }

  type RecommendPictureVO = {
    id?: number
    name?: string
    picColor?: string
    picHeight?: number
    picScale?: number
    picWidth?: number
    tags?: string[]
    thumbnailEndpoint?: string
    thumbnailUrl?: string
  }

  type RecoveryActionVO = {
    actionId?: string
    attempt?: number
    resultSummary?: string
    status?: string
    toolName?: string
    updateTime?: string
  }

  type RecoveryStateVO = {
    actions?: RecoveryActionVO[]
    activeExecution?: boolean
    backupAttempts?: number
    backupError?: string
    legacyArchiveOnly?: boolean
    pendingConfirmation?: string
    pendingReviewCount?: number
    reason?: string
    resumable?: boolean
    runStatus?: string
    stateVersion?: number
    threadId?: string
  }

  type ResourceGrantListVO = {
    canManage?: boolean
    grants?: ResourceGrantVO[]
  }

  type ResourceGrantVO = {
    createTime?: string
    expiresTime?: string
    grantedByUserId?: string
    grantedByUserName?: string
    granteeUserId?: string
    granteeUserName?: string
    id?: string
    reason?: string
    resourceId?: string
    resourceName?: string
    resourceType?: string
    revokeReason?: string
    revokeTime?: string
    revokedByUserId?: string
    status?: string
  }

  type ResourceModerationRequest = {
    reason?: string
    resourceId?: string
    resourceType?: string
  }

  type resumeUsingPostParams = {
    threadId: string
    expectedVersion?: number
  }

  type SearchPictureByColorRequest = {
    picColor?: string
    spaceId?: number
  }

  type SearchPictureByPictureRequest = {
    pictureId?: number
  }

  type SessionVO = {
    createdTime?: string
    lastMessage?: string
    messageCount?: number
    sessionId?: string
    summaryContent?: string
    title?: string
    updatedTime?: string
  }

  type Space = {
    createTime?: string
    editTime?: string
    id?: number
    isDelete?: number
    maxCount?: number
    maxSize?: number
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    totalCount?: number
    totalSize?: number
    updateTime?: string
    userId?: number
  }

  type SpaceAddRequest = {
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
  }

  type SpaceCategoryAnalyzeRequest = {
    queryAll?: boolean
    queryPublic?: boolean
    spaceId?: number
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
    maxCount?: number
    maxSize?: number
    text?: string
    value?: number
  }

  type SpaceQueryRequest = {
    current?: number
    id?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    userId?: number
  }

  type SpaceRankAnalyzeRequest = {
    topN?: number
  }

  type SpaceSizeAnalyzeRequest = {
    queryAll?: boolean
    queryPublic?: boolean
    spaceId?: number
  }

  type SpaceSizeAnalyzeResponse = {
    count?: number
    sizeRange?: string
  }

  type SpaceTagAnalyzeRequest = {
    queryAll?: boolean
    queryPublic?: boolean
    spaceId?: number
  }

  type SpaceTagAnalyzeResponse = {
    count?: number
    tag?: string
  }

  type SpaceUpdateRequest = {
    id?: number
    maxCount?: number
    maxSize?: number
    spaceLevel?: number
    spaceName?: string
  }

  type SpaceUsageAnalyzeRequest = {
    queryAll?: boolean
    queryPublic?: boolean
    spaceId?: number
  }

  type SpaceUsageAnalyzeResponse = {
    countUsageRatio?: number
    maxCount?: number
    maxSize?: number
    sizeUsageRatio?: number
    usedCount?: number
    usedSize?: number
  }

  type SpaceUser = {
    createTime?: string
    id?: number
    spaceId?: number
    spaceRole?: string
    updateTime?: string
    userId?: number
  }

  type SpaceUserAddRequest = {
    spaceId?: number
    spaceRole?: string
    userId?: number
  }

  type SpaceUserAnalyzeRequest = {
    queryAll?: boolean
    queryPublic?: boolean
    spaceId?: number
    timeDimension?: string
    userId?: number
  }

  type SpaceUserAnalyzeResponse = {
    count?: number
    period?: string
  }

  type SpaceUserEditRequest = {
    id?: number
    spaceRole?: string
  }

  type SpaceUserQueryRequest = {
    id?: number
    spaceId?: number
    spaceRole?: string
    userId?: number
  }

  type SpaceUserVO = {
    createTime?: string
    id?: number
    space?: SpaceVO
    spaceId?: number
    spaceRole?: string
    updateTime?: string
    user?: UserVO
    userId?: number
  }

  type SpaceVO = {
    createTime?: string
    editTime?: string
    id?: number
    maxCount?: number
    maxSize?: number
    permissionList?: string[]
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    totalCount?: number
    totalSize?: number
    updateTime?: string
    user?: UserVO
    userId?: number
  }

  type StorageConfigActiveRequest = {
    id?: number
  }

  type StorageConfigAddRequest = {
    accessKey?: string
    basePath?: string
    bucket?: string
    domain?: string
    endpoint?: string
    isActive?: number
    isDefault?: number
    orderNum?: number
    platform?: string
    platformName?: string
    region?: string
    secretKey?: string
    status?: number
  }

  type StorageConfigQueryRequest = {
    current?: number
    id?: number
    pageSize?: number
    platform?: string
    sortField?: string
    sortOrder?: string
    status?: number
  }

  type StorageConfigUpdateRequest = {
    accessKey?: string
    basePath?: string
    bucket?: string
    domain?: string
    endpoint?: string
    id?: number
    isActive?: number
    isDefault?: number
    orderNum?: number
    platform?: string
    platformName?: string
    region?: string
    secretKey?: string
    status?: number
  }

  type StorageConfigVO = {
    accessKeyMasked?: string
    basePath?: string
    bucket?: string
    createTime?: string
    domain?: string
    endpoint?: string
    hasAccessKey?: boolean
    hasSecretKey?: boolean
    id?: number
    isActive?: number
    isDefault?: number
    orderNum?: number
    platform?: string
    platformName?: string
    region?: string
    secretKeyMasked?: string
    status?: number
    updateTime?: string
  }

  type TaskMetrics = {
    failed?: number
    succeeded?: number
    total?: number
  }

  type thumbnailUsingGetParams = {
    pictureId: number
  }

  type UserAddRequest = {
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserPictureStatsResponse = {
    likeCount?: number
    reviewPassCount?: number
    reviewPassRate?: number
    uploadCount?: number
  }

  type UserQueryRequest = {
    current?: number
    id?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    userAccount?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    checkPassword?: string
    userAccount?: string
    userPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    createTime?: string
    id?: number
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
    vipExpireTime?: string
    vipNumber?: number
  }

  type VipExchangeRequest = {
    vipCode?: string
  }

  type VipExchangeResult = {
    status?: string
    vipExpireTime?: string
  }

  // === 生成后补丁：尾下划线别名（scripts/postgen-typings.mjs） ===
  type BaseResponseAdminDashboardStatsVO_ = BaseResponseAdminDashboardStatsVO
  type BaseResponseBoolean_ = BaseResponseBoolean
  type BaseResponseCreateOutPaintingTaskResponse_ = BaseResponseCreateOutPaintingTaskResponse
  type BaseResponseFeedbackVO_ = BaseResponseFeedbackVO
  type BaseResponseGetOutPaintingTaskResponse_ = BaseResponseGetOutPaintingTaskResponse
  type BaseResponseInteger_ = BaseResponseInteger
  type BaseResponseListImageSearchResult_ = BaseResponseListImageSearchResult
  type BaseResponseListPictureVO_ = BaseResponseListPictureVO
  type BaseResponseListSpace_ = BaseResponseListSpace
  type BaseResponseListSpaceCategoryAnalyzeResponse_ = BaseResponseListSpaceCategoryAnalyzeResponse
  type BaseResponseListSpaceLevel_ = BaseResponseListSpaceLevel
  type BaseResponseListSpaceSizeAnalyzeResponse_ = BaseResponseListSpaceSizeAnalyzeResponse
  type BaseResponseListSpaceTagAnalyzeResponse_ = BaseResponseListSpaceTagAnalyzeResponse
  type BaseResponseListSpaceUserAnalyzeResponse_ = BaseResponseListSpaceUserAnalyzeResponse
  type BaseResponseListSpaceUserVO_ = BaseResponseListSpaceUserVO
  type BaseResponseLoginUserVO_ = BaseResponseLoginUserVO
  type BaseResponseLong_ = BaseResponseLong
  type BaseResponsePageFeedbackVO_ = BaseResponsePageFeedbackVO
  type BaseResponsePagePicture_ = BaseResponsePagePicture
  type BaseResponsePagePictureVO_ = BaseResponsePagePictureVO
  type BaseResponsePageSpace_ = BaseResponsePageSpace
  type BaseResponsePageSpaceVO_ = BaseResponsePageSpaceVO
  type BaseResponsePageUserVO_ = BaseResponsePageUserVO
  type BaseResponsePicture_ = BaseResponsePicture
  type BaseResponsePictureTagCategory_ = BaseResponsePictureTagCategory
  type BaseResponsePictureVO_ = BaseResponsePictureVO
  type BaseResponseSpace_ = BaseResponseSpace
  type BaseResponseSpaceUsageAnalyzeResponse_ = BaseResponseSpaceUsageAnalyzeResponse
  type BaseResponseSpaceUser_ = BaseResponseSpaceUser
  type BaseResponseSpaceVO_ = BaseResponseSpaceVO
  type BaseResponseString_ = BaseResponseString
  type BaseResponseUserPictureStatsResponse_ = BaseResponseUserPictureStatsResponse
  type BaseResponseUserVO_ = BaseResponseUserVO
  type BaseResponseVipExchangeResult_ = BaseResponseVipExchangeResult
  type PageFeedbackVO_ = PageFeedbackVO
  type PagePicture_ = PagePicture
  type PagePictureVO_ = PagePictureVO
  type PageSpace_ = PageSpace
  type PageSpaceVO_ = PageSpaceVO
  type PageUserVO_ = PageUserVO
}
