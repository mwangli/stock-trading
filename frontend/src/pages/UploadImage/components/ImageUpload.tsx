import React from 'react';
import {InboxOutlined} from '@ant-design/icons';
import type {UploadProps} from 'antd';
import {message, Upload} from 'antd';
import {upload} from '@/services/ant-design-pro/api';

const {Dragger} = Upload;

const props: UploadProps = {
  name: 'file',
  multiple: true,

  onChange: function (info) {
    const {status} = info.file;
    if (status !== 'uploading') {
      console.log(info.file, info.fileList);
    }
    if (status === 'done') {
      upload(info.file).then(res => {
        if (res.success) message.success(`文件上传成功！缓存总文件数量为 ${res.data}`);
      })
    } else if (status === 'error') {
      message.error(`${info.file.name} 文件上传失败`).then(r => {});
    }
  },
  onDrop(e) {
    console.log('Dropped files', e.dataTransfer.files);
  },

};

const App: React.FC = () => (
  <Dragger {...props}>
    <p className="ant-upload-drag-icon">
      <InboxOutlined/>
    </p>
    <p className="ant-upload-text">点击上传文件</p>
    <p className="ant-upload-hint">
      支持拖动文件到该区域进行文件上传
    </p>
  </Dragger>
);

export default App;
