import {download} from '@/services/ant-design-pro/api';
import {PageContainer,} from '@ant-design/pro-components';
import {Button, Flex, message} from 'antd';
import React from 'react';
import UploadForm from "@/pages/UploadImage/components/ImageUpload";
import {DownloadOutlined} from "@ant-design/icons";


const TableList: React.FC = () => {

    function getNowDate() {
      const now = new Date();
      const year = now.getFullYear();
      const month = ('0' + (now.getMonth() + 1)).slice(-2);
      const day = ('0' + now.getDate()).slice(-2);
      const hours = ('0' + now.getHours()).slice(-2);
      const minutes = ('0' + now.getMinutes()).slice(-2);
      const seconds = ('0' + now.getSeconds()).slice(-2);
      return year + month + day + hours + minutes + seconds;
    }

    /* 下载文件的公共方法，参数就传blob文件流*/
    function handleExport(data: any) {
      // 动态创建iframe下载文件
      let fileName = "test-" + getNowDate() + ".xlsx";
      if (!data) {
        return;
      }
      let blob = new Blob([data], {type: "application/octet-stream"});
      if ("download" in document.createElement("a")) {
        // 不是IE浏览器
        let url = window.URL.createObjectURL(blob);
        let link = document.createElement("a");
        link.style.display = "none";
        link.href = url;
        link.setAttribute("download", fileName);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link); // 下载完成移除元素
        window.URL.revokeObjectURL(url); // 释放掉blob对象
      } else {
        // IE 10+
        // window.navigator.msSaveBlob(blob, fileName);
      }
    }


    return (
      <PageContainer>
        <UploadForm></UploadForm>
        <Flex vertical={true}>
          <Button type="primary"
                  icon={<DownloadOutlined/>}
                  size={"large"}
                  onClick={() => {
                    download({}).then((blob) => {
                      if (blob) handleExport(blob)
                      else message.error("文件下载失败!")
                    })
                  }}>
            下载表格
          </Button>
        </Flex>

      </PageContainer>
    );
  }
;

export default TableList;
