import {ProFormSwitch, ProFormText, ProFormTextArea, ProFormSelect, StepsForm,} from '@ant-design/pro-components';
import {FormattedMessage, useIntl} from '@umijs/max';
import {Modal} from 'antd';
import React from 'react';

export type FormValueType = {
  target?: string;
  template?: string;
  type?: string;
  time?: string;
  frequency?: string;
} & Partial<API.RuleListItem>;

export type UpdateFormProps = {
  onCancel: (flag?: boolean, formVals?: FormValueType) => void;
  onSubmit: (values: FormValueType) => Promise<void>;
  updateModalOpen: boolean;
  values: Partial<API.RuleListItem>;
};

const UpdateForm: React.FC<UpdateFormProps> = (props) => {
  const intl = useIntl();
  return (
    <StepsForm
      stepsProps={{
        size: 'default',
      }}
      stepsFormRender={(dom, submitter) => {
        return (
          <Modal
            width={640}
            bodyStyle={{padding: '32px 40px 48px'}}
            destroyOnClose
            title={intl.formatMessage({
              id: 'pages.searchTable.updateForm.ruleConfig',
              defaultMessage: '规则配置',
            })}
            open={props.updateModalOpen}
            footer={submitter}
            onCancel={() => {
              props.onCancel();
            }}
          >
            {dom}
          </Modal>
        );
      }}
      onFinish={props.onSubmit}
    >
      <StepsForm.StepForm
        initialValues={props.values}
        title={intl.formatMessage({
          id: 'pages.searchTable.updateForm.basicConfig',
          defaultMessage: '基本信息',
        })}
      >
        <ProFormTextArea width="md" name="id" hidden initialValue={props.values?.id}/>

        <ProFormText width="md" name="id" hidden={true}/>

        <ProFormText width="md" name="sort" label={"任务排序"}/>
        <ProFormText
          rules={[
            {
              required: true,
              message: (
                <FormattedMessage
                  id="pages.searchTable.ruleName"
                  defaultMessage="Rule name is required"
                />
              ),
            },
          ]}
          width="md"
          name="name"
          label={intl.formatMessage({
            id: 'pages.searchTable.jobName',
            defaultMessage: '任务名称',
          })}
        />
        <ProFormTextArea width="md" name="description" label={intl.formatMessage({
          id: 'pages.searchTable.jobDescription',
          defaultMessage: '任务描述',
        })}/>
        <ProFormTextArea width="md" name="className"
                         label={intl.formatMessage({
                           id: 'pages.searchTable.jobClassName',
                           defaultMessage: '任务全类名',
                         })}
                         rules={[
                           {
                             required: true,
                             message: (
                               <FormattedMessage
                                 id="pages.modalForm.message.className"
                                 defaultMessage="className is required"
                               />
                             ),
                           }]}/>
        <ProFormText width="md" name="cron"
                     label={intl.formatMessage({
                       id: 'pages.searchTable.jobCronExpression',
                       defaultMessage: '执行表达式',
                     })}
                     rules={[
                       {
                         required: true,
                         message: (
                           <FormattedMessage
                             id="pages.modalForm.message.cron"
                             defaultMessage="jobCronExpression is required"
                           />
                         ),
                       }]}
        />
        <ProFormTextArea width="md" name="token"
                         label={intl.formatMessage({
                           id: 'pages.searchTable.token',
                           defaultMessage: 'token',
                         })}
                         hidden={true}
        />
        <ProFormSelect
          width="md"
          name="logSwitch"
          label="是否开启接口日志"
          initialValue={''}
          valueEnum={{
            open: '开启',
            close: '关闭',
          }}
          placeholder="默认关闭"
        />
        <ProFormSelect
          width="md"
          name="skipWaiting"
          label="是否启用跳过训练"
          initialValue={''}
          valueEnum={{
            enable: '启用',
            disable: '关闭',
          }}
          placeholder="默认关闭"
        />
      </StepsForm.StepForm>

    </StepsForm>
  );
};

export default UpdateForm;
