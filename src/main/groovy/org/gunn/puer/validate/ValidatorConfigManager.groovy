package org.gunn.puer.validate

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import org.springframework.util.ResourceUtils;
import org.springframework.core.io.Resource;

class ValidatorConfigManager implements Validator{

	def validateRuleContainer = [:]

	Logger logger = LoggerFactory.getLogger(ValidatorConfigManager)
	Resource resource;
	

	def init(){
		logger.info("init the validator config manager with validator.properties")
		InputStream inputsteam = null
                if(resource != null){
		   inputsteam=resource.getInputStream();
		}else{
		   inputsteam=ValidatorConfigManager.class.getClass().getClassLoader().getResourceAsStream("/validator.groovy")
		}		
		//logger.info(ValidatorConfigManager.getClass().getResource("validator.groovy").getPath())
		//String configContext
		// ResourceUtils.getFile("classpath:validator.groovy").each{ it ->  configContext = configContext + "\n" + it}
		//logger.info(configContext)
		configValidateRules(inputsteam)
	}

	def configValidateRules(InputStream inputsteam, boolean isAppend = false){
		logger.info("begin config validate")
		def validatorRules = runDSL(inputsteam)
		if(isAppend){
			validatorRules.each{ key , value ->
				def oldRules = validateRuleContainer[key]
				oldRules = oldRules + value
				validateRuleContainer[key] = oldRules
			}
		} else {
			validateRuleContainer = validatorRules
		}
	}

	/**
	 * 通过运行DSL，获取配置对象。
	 * @return
	 */
	def runDSL(InputStream inputsteam){
		Script dslScript = new GroovyShell().parse(new InputStreamReader(inputsteam));;
		dslScript.metaClass.mixin(SpringValidatorConfigureationDelegate)
		return dslScript.run();
	}

	boolean supports(Class<?> clazz){
		if(validateRuleContainer.size() == 0){
			init()
		}
		return getValidateRule(clazz) != null
	}

	void validate(Object target, Errors errors){
		POJOValidatorRule rule = getValidateRule(target.getClass())
        def beforeValidateMethod = target.getMetaClass().getMetaMethod("beforeValidate")
        if(beforeValidateMethod != null){
            beforeValidateMethod.invoke(target, null)
        }
		rule?.validate(target, errors)
		target.properties.each {key,val ->
			[Collection, Object[]].any(){
				if(it.isAssignableFrom(val.getClass())){
					val.each{ validate(it, errors) }
				}
			}
		}
	}

	void validate(Object target, Errors errors , String...groups){
		POJOValidatorRule rule = getValidateRule(target.getClass())
		rule?.validate(target, errors, groups)
	}


	def getValidateRule(Class<?> clazz){
		def rule = null
		logger.debug("the all rule is:" + validateRuleContainer)
		return rule?:validateRuleContainer["*"]?.get(clazz.getName())
	}
}
