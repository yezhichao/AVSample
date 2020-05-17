# SurfaceView实现相机预览，实现拍照功能
### 目标
1. 熟悉Camera1的api使用

### 注意点
1. 应用窗口的方向、相机预览方向、相机拍摄出片的方向各有差别，要注意按照google的要求，进行旋转处理，可参考：

- https://glumes.com/post/android/android-camera-aspect-ratio--and-orientation/
- https://my.oschina.net/madmatrix/blog/204333
- https://developer.android.com/guide/topics/media/camera?hl=cn#manifest

### 未完成的点
 - [ ] 长宽比例适配
 - [ ] camera相关的操作可以放到一个线程里去
 - [ ] 动态权限