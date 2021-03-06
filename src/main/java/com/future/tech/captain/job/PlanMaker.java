/**
 * 
 */
package com.future.tech.captain.job;

import java.util.List;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.future.tech.captain.api.CorrelationData;
import com.future.tech.captain.api.MessageConfirmChecker;
import com.future.tech.captain.api.exception.NotSureException;
import com.future.tech.captain.config.CaptainConfig;
import com.future.tech.captain.domain.MessageWrapper;
import com.future.tech.captain.domain.MessageWrapperIdentity;
import com.future.tech.captain.mq.MessageSender;
import com.future.tech.captain.repository.MessageRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * Title: PlanMaker.java<br>
 * Description: <br>
 * Copyright: Copyright (c) 2017<br>
 * Company: FutureTech<br>
 * 
 * @author weilai May 22, 2017
 */

@Component("captain.defaultPlanMaker")
@Slf4j
public class PlanMaker {

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private CaptainConfig config;

	public void makeAndDoPlan() {
		List<MessageWrapper> messageList = messageRepository.findAllPreparedByAppName(config.getAppName(),
				config.getPlanLimit());
		if (!CollectionUtils.isEmpty(messageList)) {
			for (MessageWrapper messageWrapper : messageList) {
				try {
					this.processMessage(messageWrapper);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}

	private void processMessage(MessageWrapper messageWrapper) throws NotSureException {
		DateTime dateTime = new DateTime().minusSeconds(config.getJobWaitSecs());
		if (dateTime.isBefore(messageWrapper.getCreatedDate().getTime())) {
			return;
		}
		MessageConfirmChecker messageConfirmChecker = config
				.findMessageConfirmChecker(messageWrapper.getMessageConfirmCheckerName());
		CorrelationData correlationData = new CorrelationData();
		MessageWrapperIdentity messageWrapperIdentity = new MessageWrapperIdentity(messageWrapper.getId());
		correlationData.setId(messageWrapperIdentity.getOriginId());
		correlationData.setMessageConfirmCheckerName(messageWrapper.getMessageConfirmCheckerName());
		correlationData.setMessageSenderName(messageWrapper.getMessageSenderName());
		boolean isNeed2Modify = false;
		if (messageConfirmChecker.isMessageNeed2Send(correlationData)) {
			MessageSender messageSender = config.findMessageSender(messageWrapper.getMessageSenderName());
			if (messageSender == null) {
				// Can not find messageSender, cancel message
				isNeed2Modify = messageWrapper.cancel();
			} else {
				messageSender.send(messageWrapper.getId(), messageWrapper.getMessage());
				isNeed2Modify = messageWrapper.confirm();
			}
		} else {
			isNeed2Modify = messageWrapper.cancel();
		}
		if (isNeed2Modify) {
			messageRepository.store(messageWrapper);
		}
	}
}
