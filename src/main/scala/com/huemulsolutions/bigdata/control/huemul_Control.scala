package com.huemulsolutions.bigdata.control


import org.apache.spark.sql.types._
import java.util.Calendar;
import com.huemulsolutions.bigdata.datalake.huemul_DataLake
import com.huemulsolutions.bigdata.common._
import com.huemulsolutions.bigdata.tables._
import com.huemulsolutions.bigdata.dataquality._
import com.huemulsolutions.bigdata.dataquality.huemulType_DQNotification._
import com.huemulsolutions.bigdata.dataquality.huemulType_DQQueryLevel._
import huemulType_Frequency._

class huemul_Control (phuemulBigDataGov: huemul_BigDataGovernance, ControlParent: huemul_Control, runFrequency: huemulType_Frequency, IsSingleton: Boolean = true, RegisterInControlLog: Boolean = true) extends Serializable  {
  val huemulBigDataGov = phuemulBigDataGov
  val Control_Id: String = huemulBigDataGov.huemul_GetUniqueId() 
  
  val Invoker = new Exception().getStackTrace()
  
  val Control_IdParent: String = if (ControlParent == null) null else ControlParent.Control_Id
  val Control_ClassName: String = Invoker(1).getClassName().replace("$", "")
  val Control_ProcessName: String = Invoker(1).getMethodName().replace("$", "")
  val Control_FileName: String = Invoker(1).getFileName.replace("$", "")
  
  var Control_Start_dt: Calendar = Calendar.getInstance()
  var Control_Stop_dt: Calendar = null
  val Control_Error: huemul_ControlError = new huemul_ControlError(huemulBigDataGov)
  val Control_Params: scala.collection.mutable.ListBuffer[huemul_LibraryParams] = new scala.collection.mutable.ListBuffer[huemul_LibraryParams]() 
  private var LocalIdStep: String = ""
  private var Step_IsDQ: Boolean = false
  private var AdditionalParamsInfo: String = ""
  
  //Find process name in control_process
  
  if (RegisterInControlLog && huemulBigDataGov.RegisterInControl)
  huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s""" SELECT
  control_process_addOrUpd(
                  '${Control_ClassName}' -- process_id
                  ,'0'  --as area_id
                  ,'${Control_ClassName}' --as process_name
                  ,'${Control_FileName}' --as process_FileName
                  ,''  --as process_description
                  ,'${runFrequency}' --p_process_frequency
                  ,''  --as process_owner
                  ,'${Control_ClassName}' --as mdm_processname
                ) 
      """)
  
  
  //Insert processExcec
  if (RegisterInControlLog && huemulBigDataGov.RegisterInControl) {
    println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] ProcessExec_Id: ${Control_Id}, processName: ${Control_ClassName}")
  huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_processExec_add (
            '${Control_Id}'  --p_processExec_id
           ,${if (Control_IdParent == null) "null" else s"'${Control_IdParent}'"}  --p_processExec_idParent
           ,'${Control_ClassName}'  --p_process_id
           ,'${huemulBigDataGov.Malla_Id}'  --p_Malla_id
           ,'${huemulBigDataGov.IdApplication}'  --p_Application_id
           , '${if (huemulBigDataGov.Malla_Id == "") "user" else "orchestrator"}' --p_processExec_WhosRun
           , ${huemulBigDataGov.DebugMode} --p_processExec_DebugMode
           , '${huemulBigDataGov.Environment}' --p_processExec_Environment

           , ${this.getparamYear()} --p_processExec_param_year 
           , ${this.getparamMonth()} --p_processExec_param_month
           , ${this.getparamDay()} --p_processExec_param_day
           , ${this.getparamHour()} --p_processExec_param_hour
           , ${this.getparamMin()} --p_processExec_param_min
           , ${this.getparamSec()} --p_processExec_param_sec
           , '' --p_processExec_param_others

           ,'${Control_ClassName}'  --p_MDM_ProcessName
          )
          """)
          }
  
  //Insert new record
  if (RegisterInControlLog)
  NewStep("Start")
  
  //*****************************************
  //Start Singleton
  //*****************************************  
  if (IsSingleton && huemulBigDataGov.RegisterInControl) {
    NewStep("SET SINGLETON MODE")
    var NumCycle: Int = 0
    var ContinueInLoop: Boolean = true
    
    
    while (ContinueInLoop) {
      //Try to add Singleton Mode      
      val Ejec =  huemulBigDataGov.postgres_connection.ExecuteJDBC_WithResult(s"""select control_singleton_Add (
                        '${Control_ClassName}'  --p_singleton_id
                       ,'${huemulBigDataGov.IdApplication}'  --p_application_Id
                       ,'${Control_ClassName}'  --p_singleton_name
                      )
                      """)

      var ApplicationInUse: String = null
      ApplicationInUse = Ejec.ResultSet(0).getString(0)        
      
      //if don't have error, exit
      if (!Ejec.IsError && ApplicationInUse == null)
        ContinueInLoop = false
      else {      
        if (huemulBigDataGov.DebugMode) println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] waiting for Singleton... (class: $Control_ClassName, appId: ${huemulBigDataGov.IdApplication} )")
        //if has error, verify other process still alive
        if (NumCycle == 1) //First cicle don't wait
          Thread.sleep(10000)
        
        NumCycle = 1
        // Obtiene procesos pendientes
        
        huemulBigDataGov.application_StillAlive(ApplicationInUse)
      }              
    }
  }
  
  //*****************************************
  //END Start Singleton
  //*****************************************  

 private var paramYear: Integer = 0
 def getparamYear(): Integer = {return paramYear}
 
 private var paramMonth: Integer = 0
 def getparamMonth(): Integer = {return paramMonth}
 
 private var paramDay: Integer = 0
 def getparamDay(): Integer = {return paramDay}
 
 private var paramHour: Integer = 0
 def getparamHour(): Integer = {return paramHour}
 
 private var paramMin: Integer = 0
 def getparamMin(): Integer = {return paramMin}
 
 private var paramSec: Integer = 0
 def getparamSec(): Integer = {return paramSec}
 
 def AddParamYear(name: String, value: Integer) {
   paramYear = value
	 AddParamInformationIntern(name, value.toString)
	 UpdateProcessExecParam("year",value)
 }
 
 def AddParamMonth(name: String, value: Integer) {
   paramMonth = value
	 AddParamInformationIntern(name, value.toString)
	 UpdateProcessExecParam("month",value)
 }
 
 def AddParamDay(name: String, value: Integer) {
   paramDay = value
	 AddParamInformationIntern(name, value.toString)
	 UpdateProcessExecParam("day",value)
 }
 
 def AddParamHour(name: String, value: Integer) {
   paramHour = value
	 AddParamInformationIntern(name, value.toString)
	 UpdateProcessExecParam("hour",value)
 }
 
 def AddParamMin(name: String, value: Integer) {
   paramMin = value
	 AddParamInformationIntern(name, value.toString)
	 UpdateProcessExecParam("min",value)
 }
 
 def AddParamSec(name: String, value: Integer) {
   paramSec = value
	 AddParamInformationIntern(name, value.toString)
	 UpdateProcessExecParam("sec",value)
 }
 
 private def UpdateProcessExecParam (paramName: String, value: Integer) {
    //Insert processExcec
    if (huemulBigDataGov.RegisterInControl) {
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_processExec_UpdParam (
                          '${this.Control_Id}'  --processexec_id
                         ,'${paramName}'  --p_paramName  
                         , ${value} --as p_value
                        )
                      """)      
    }
 }
  
 
 private def UpdateProcessExecParamInfo (paramName: String, value: String) {
    AdditionalParamsInfo = s"${AdditionalParamsInfo}${if (AdditionalParamsInfo == "") "" else ", "} {${paramName}}=${value}"
    
    //Insert processExcec
    if (huemulBigDataGov.RegisterInControl) {
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_processExec_UpdParamInfo (
                          '${this.Control_Id}'  --processexec_id
                         ,'${paramName}'  --p_paramName  
                         , '${if (value == null) "" else value.replace("'", "''")}' --as p_value
                        )
                      """)      
    }
 }
 
 private def AddParamInformationIntern(name: String, value: String) {
    val NewParam = new huemul_LibraryParams()
    NewParam.param_name = name
    NewParam.param_value = value
    NewParam.param_type = "function"
    Control_Params += NewParam
          
    
    
    //Insert processExcec
    if (huemulBigDataGov.RegisterInControl) {
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_ProcessExecParams_add (
                           '${this.Control_Id}'  --processexec_id
                         , '${NewParam.param_name}' --as processExecParams_Name
                         , '${NewParam.param_value}' --as processExecParams_Value
                         ,'${Control_ClassName}'  --process_id
                        )
                      """)      
    }

    if (huemulBigDataGov.DebugMode){
      println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] Param num: ${Control_Params.length}, name: $name, value: $value")
    }
   
 }
  
 def AddParamInformation(name: String, value: String) {
   //add to param table
   AddParamInformationIntern(name, value)
   
   //Update in processExec paramAddInfo
   UpdateProcessExecParamInfo(name, value)
  }
    
  def FinishProcessOK {
    
    if (huemulBigDataGov.RegisterInControl) {
      if (!huemulBigDataGov.HasName(Control_IdParent)) println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] FINISH ALL OK")
      println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] FINISH ProcessExec_Id: ${Control_Id}, processName: ${Control_ClassName}")
    
    
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_processExec_Finish (
                             '${Control_Id}'  --p_processExec_id
                           , '${this.LocalIdStep}' --as p_processExecStep_id
                           ,  null --as p_error_id
                           )
                        """)   
                        
      if (this.IsSingleton) {
          huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_singleton_remove (
                             '${Control_ClassName}'  --p_processExec_id
                           )
                        """)  
        }
    }
  }
  
  def FinishProcessError() {
    
      
    if (huemulBigDataGov.RegisterInControl) {
      if (Control_IdParent == null) println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] FINISH ERROR")
      println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] FINISH ProcessExec_Id: ${Control_Id}, processName: ${Control_ClassName}")
    
      val Error_Id = huemulBigDataGov.huemul_GetUniqueId()
      if (Control_Error.ControlError_Message == null)
        Control_Error.ControlError_Message = ""
      if (Control_Error.ControlError_Trace == null)
        Control_Error.ControlError_Trace = ""
      if (Control_Error.ControlError_FileName == null)
        Control_Error.ControlError_FileName = ""
      if (Control_Error.ControlError_MethodName == null)
        Control_Error.ControlError_MethodName = ""
      if (Control_Error.ControlError_ClassName == null)
        Control_Error.ControlError_ClassName = ""
        
      //Insert processExcec
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_Error_finish (
                          '${this.Control_Id}'  --p_processExec_id
                         , '${this.LocalIdStep}'  --p_processExecStep_id
                         , '${Error_Id}'  --Error_Id
                         , '${Control_Error.ControlError_Message.replace("'", "''")}' --as Error_Message
                         , ${Control_Error.ControlError_ErrorCode} --as error_code
                         , '${Control_Error.ControlError_Trace.replace("'", "''")}' --as Error_Trace
                         , '${Control_Error.ControlError_ClassName.replace("'", "''")}' --as Error_ClassName
                         , '${Control_Error.ControlError_FileName.replace("'", "''")}' --as Error_FileName
                         , '${Control_Error.ControlError_LineNumber}' --as Error_LIneNumber
                         , '${Control_Error.ControlError_MethodName.replace("'", "''")}' --as Error_MethodName
                         , '' --as Error_Detail
                         ,'${Control_ClassName}'  --process_id
                    )
                      """)            
      if (this.IsSingleton) {
        huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_singleton_remove (
                           '${Control_ClassName}'  --p_processExec_id
                         )
                      """)  
      }
    }
        
                               
  }
  
  
  def RegisterError(ErrorCode: Integer, Message: String, Trace: String, FileName: String, MethodName: String, ClassName: String, LineNumber: Integer, WhoWriteError: String ) {
    
      
    if (huemulBigDataGov.RegisterInControl) {
      
      val Error_Id = huemulBigDataGov.huemul_GetUniqueId()
      
      var message = Message
      if (message == null)
        message = ""
        
      var trace = Trace
      if (trace == null)
        trace = ""
        
      var fileName = FileName
      if (fileName == null)
        fileName = ""
        
      var methodName = MethodName
      if (methodName == null)
        methodName = ""
        
      var className = ClassName
      if (className == null)
        className = ""
        
      //Insert processExcec
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_Error_register (
                          '${Error_Id}'  --Error_Id
                         , '${message.replace("'", "''")}' --as Error_Message
                         , ${ErrorCode} --as error_code
                         , '${trace.replace("'", "''")}' --as Error_Trace
                         , '${className.replace("'", "''")}' --as Error_ClassName
                         , '${fileName.replace("'", "''")}' --as Error_FileName
                         , '${LineNumber}' --as Error_LIneNumber
                         , '${methodName.replace("'", "''")}' --as Error_MethodName
                         , '' --as Error_Detail
                         ,'${WhoWriteError}'  --process_id
                    )
                      """)            
     
    }
        
                               
  }
  
  
  def NewStep(StepName: String) {
    println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] Step: $StepName")
    
   
    //New Step add
    val PreviousLocalIdStep = LocalIdStep
    LocalIdStep = huemulBigDataGov.huemul_GetUniqueId()
    
    if (huemulBigDataGov.RegisterInControl) {
      //Insert processExcec
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""SELECT control_processExecStep_add (
                  '${LocalIdStep}'  --p_processExecStep_id
                 ,'${PreviousLocalIdStep}'  --p_processExecStep_idAnt
                 ,'${this.Control_Id}'  --p_processExec_id
                 , '${StepName}' --p_processExecStep_Name
                 ,'${Control_ClassName}'  --p_MDM_ProcessName
                )
          """)
    }
  }
  
  def RegisterTestPlanFeature(p_Feature_Id: String
                             ,p_TestPlan_Id: String) {
    if (huemulBigDataGov.RegisterInControl) {
       //Insert processExcec
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""SELECT control_TestPlanFeature_add (
                            '${if (p_Feature_Id == null) "" else p_Feature_Id.replace("'", "''")}'  --as p_Feature_Id
                         , '${p_TestPlan_Id}'  --as p_testPlan_Id
                         ,'${Control_ClassName}'  --p_MDM_ProcessName
                                                  
                        )""")
    }
    
    
  }
  
  /**
   * Return TestPlan ID
   */
  def RegisterTestPlan(p_testPlanGroup_Id: String
                        ,p_testPlan_name: String
                        ,p_testPlan_description: String
                        ,p_testPlan_resultExpected: String
                        ,p_testPlan_resultReal: String
                        ,p_testPlan_IsOK: Boolean): String = {
    //Create New Id
    val testPlan_Id = huemulBigDataGov.huemul_GetUniqueId()
    
    //if (!p_testPlan_IsOK) {
      println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}] TestPlan ${if (p_testPlan_IsOK) "OK " else "ERROR " }: testPlan_name: ${p_testPlan_name}, resultExpected: ${p_testPlan_resultExpected}, resultReal: ${p_testPlan_resultReal} ")
    //}
                     
    if (huemulBigDataGov.RegisterInControl) {
       //Insert processExcec
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""SELECT control_TestPlan_add (
                        '${testPlan_Id}'  --as p_testPlan_Id
                         , '${p_testPlanGroup_Id}'  --as p_testPlanGroup_Id
                         , '${this.Control_Id}'  --p_processExec_id
                         , '${this.Control_ClassName}' --as p_process_id
                         , '${if (p_testPlan_name == null) "" else p_testPlan_name.replace("'", "''")}' --p_testPlan_name
                         , '${if (p_testPlan_description == null) "" else p_testPlan_description.replace("'", "''")}' --p_testPlan_description
                         , '${if (p_testPlan_resultExpected == null) "" else p_testPlan_resultExpected.replace("'", "''")}' --p_testPlan_resultExpected
                         , '${if (p_testPlan_resultReal == null) "" else p_testPlan_resultReal.replace("'", "''")}' --p_testPlan_resultReal
                         , ${p_testPlan_IsOK} --p_testPlan_IsOK
                         , '${Control_ClassName}' --p_Executor_Name
                         
                        )""")
    }
    
    return testPlan_Id
  }
  
  def RegisterDQuality (Table_Name: String
                             , BBDD_Name: String
                             , DF_Alias: String
                             , ColumnName: String
                             , DQ_Name: String
                             , DQ_Description: String
                             , DQ_QueryLevel: huemulType_DQQueryLevel //DQ_IsAggregate: Boolean
                             , DQ_Notification: huemulType_DQNotification ////DQ_RaiseError: Boolean
                             , DQ_SQLFormula: String
                             , DQ_toleranceError_Rows: java.lang.Long
                             , DQ_toleranceError_Percent: Decimal
                             , DQ_ResultDQ: String
                             , DQ_ErrorCode: Integer
                             , DQ_NumRowsOK: Long
                             , DQ_NumRowsError: Long
                             , DQ_NumRowsTotal: Long
                             , DQ_IsError: Boolean) {
                
    //Create New Id
    val DQId = huemulBigDataGov.huemul_GetUniqueId()

    if (huemulBigDataGov.RegisterInControl) {
      //Insert processExcec
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""SELECT control_DQ_add (
                        '${DQId}'  --as p_DQ_Id
                         , '${Table_Name}'  --as Table_name
                         , '${BBDD_Name}'  --asp_BBDD_name
                         , '${this.Control_ClassName}' --as p_Process_Id
                         , '${this.Control_Id}' -- p_ProcessExec_Id
                         , ${if (ColumnName == null) "null" else s"'${ColumnName}'"} --Column_Name
                         , '${DF_Alias}' --p_Dq_AliasDF
                         , '${DQ_Name}' --p_DQ_Name
                         , '${DQ_Description}' --DQ_Description
                         , '${DQ_QueryLevel}' --DQ_IsAggregate
                         , '${DQ_Notification}' --DQ_RaiseError
                         , '${if (DQ_SQLFormula ==  null) "" else DQ_SQLFormula.replace("'", "''")}' --DQ_SQLFormula
                         , ${DQ_toleranceError_Rows} --DQ_Error_MaxNumRows
                         , ${DQ_toleranceError_Percent} --DQ_Error_MaxPercent
                         , '${if (DQ_ResultDQ == null) "" else DQ_ResultDQ.replace("'", "''")}' --DQ_ResultDQ
                         , ${DQ_ErrorCode} --DQ_ErrorCode
                         , ${DQ_NumRowsOK} --DQ_NumRowsOK
                         , ${DQ_NumRowsError} --DQ_NumRowsError
                         , ${DQ_NumRowsTotal} --DQ_NumRowsTotal
                         , ${DQ_IsError} --DQ_IsError
                         ,'${Control_ClassName}'  --process_id
                        )""")
    }
    
  }
  
  def GetCharRepresentation(value: String): String  = {
    return if (value == "\t") "TAB"
    else value
           
  }
  
  def RegisterRAW_USE(dapi_raw: huemul_DataLake) {        
    dapi_raw.setrawFiles_id(huemulBigDataGov.huemul_GetUniqueId())
    
    if (huemulBigDataGov.RegisterInControl) {
      //Insert processExcec
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""
        select control_rawFiles_add ( '${dapi_raw.getrawFiles_id}'  --p_RAWFiles_id
                         , '${dapi_raw.LogicalName}' --p_RAWFiles_LogicalName
                         , '${dapi_raw.GroupName.toUpperCase()}' --p_RAWFiles_GroupName
                         , '${dapi_raw.Description}' -- p_RAWFiles_Description
                         , '${dapi_raw.SettingInUse.ContactName}' --p_RAWFiles_Owner
                         ,'${dapi_raw.getFrequency}'  --p_RAWFiles_Frequency
                         ,'${Control_ClassName}'  --MDM_ProcessName
                      )
                      """)
                      
      //Insert Config Details
      dapi_raw.SettingByDate.foreach { x => 
        //Insert processExcec
        val RAWFilesDet_id = huemulBigDataGov.huemul_GetUniqueId()
        huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""SELECT control_RAWFilesDet_add (
                               '${RAWFilesDet_id}' --as RAWFilesDet_id
                             , '${dapi_raw.LogicalName}' --p_RAWFiles_LogicalName
                             , '${dapi_raw.GroupName.toUpperCase()}' --p_RAWFiles_GroupName
                             ,'${huemulBigDataGov.dateTimeFormat.format(x.StartDate.getTime) }'  --RAWFilesDet_StartDate
                             ,'${huemulBigDataGov.dateTimeFormat.format(x.EndDate.getTime) }'  --RAWFilesDet_EndDate
                             ,'${x.FileName }'  --RAWFilesDet_FileName
                             ,'${x.LocalPath }'  --RAWFilesDet_LocalPath
                             ,'${x.GetPath(x.GlobalPath) }'  --RAWFilesDet_GlobalPath
                             ,'${x.DataSchemaConf.ColSeparatorType }'  --RAWFilesDet_Data_ColSeparatorType
                             ,'${GetCharRepresentation(x.DataSchemaConf.ColSeparator) }'  --RAWFilesDet_Data_ColSeparator
                             ,''  --RAWFilesDet_Data_HeaderColumnsString
                             ,'${x.LogSchemaConf.ColSeparatorType }'  --RAWFilesDet_Log_ColSeparatorType
                             ,'${GetCharRepresentation(x.LogSchemaConf.ColSeparator) }'  --RAWFilesDet_Log_ColSeparator
                             ,''  --RAWFilesDet_Log_HeaderColumnsString
                             ,'${x.LogNumRows_FieldName }'  --RAWFilesDet_Log_NumRowsFieldName
                             ,'${x.ContactName }'  --RAWFilesDet_ContactName
                             ,'${Control_ClassName}'  --process_id
                            )
                          """)   
         
         if (x.DataSchemaConf.ColumnsDef != null) {
           var pos: Integer = 0
           x.DataSchemaConf.ColumnsDef.foreach { y =>
                 huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""SELECT control_RAWFilesDetFields_add(
                                 '${dapi_raw.LogicalName}' --p_RAWFiles_LogicalName
                               , '${dapi_raw.GroupName.toUpperCase()}' --p_RAWFiles_GroupName
                               ,'${huemulBigDataGov.dateTimeFormat.format(x.StartDate.getTime) }'  --RAWFilesDet_StartDate
                               ,'${y.getcolumnName_TI}'  --RAWFilesDetFields_ITName
                               ,'${y.getcolumnName_Business}' --as RAWFilesDetFields_LogicalName
                               ,'${if (y.getDescription == null) "" else y.getDescription.replace("'", "''")}' --as RAWFilesDetFields_description
                               ,'${y.getDataType}' --as RAWFilesDetFields_DataType
                               ,${pos }  --RAWFilesDetFields_Position
                               ,${y.getPosIni}  --RAWFilesDetFields_PosIni
                               ,${y.getPosFin}  --RAWFilesDetFields_PosFin
                               , ${y.getApplyTrim} --,RAWFilesDetFields_ApplyTrim     
                               , ${y.getConvertToNull} --,RAWFilesDetFields_ConvertNull   
                               ,'${Control_ClassName}'  --process_id
                          )
                            """)   
                            
                 pos += 1
             }
         }
      }
    
      //Insert control_rawFilesUse
      val rawfilesuse_id = huemulBigDataGov.huemul_GetUniqueId()
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_rawFilesUse_add (
                           '${dapi_raw.LogicalName}' --p_RAWFiles_LogicalName
                         , '${dapi_raw.GroupName.toUpperCase()}' --p_RAWFiles_GroupName
                         ,'${rawfilesuse_id }'  --rawfilesuse_id
                         ,'${this.Control_ClassName}'  --process_id
                         ,'${this.Control_Id}'          --processExec_Id
                         , ${dapi_raw.SettingInUse.getuse_year} -- RAWFilesUse_Year
                         , ${dapi_raw.SettingInUse.getuse_month} -- ,RAWFilesUse_Month
                         , ${dapi_raw.SettingInUse.getuse_day} -- RAWFilesUse_Day 
                         , ${dapi_raw.SettingInUse.getuse_hour} -- RAWFilesUse_Hour
                         , ${dapi_raw.SettingInUse.getuse_minute} -- RAWFilesUse_Miute
                         , ${dapi_raw.SettingInUse.getuse_second} -- RAWFilesUse_Second
                         , '${dapi_raw.SettingInUse.getuse_params}' -- RAWFilesUse_params
                         ,'${dapi_raw.FileName}' --RAWFiles_FullName
                         ,'${dapi_raw.FileName}' --RAWFiles_FullPath
                         ,'${dapi_raw.DataFramehuemul.getNumRows}' --RAWFiles_NumRows
                         , '' --RAWFiles_HeaderLine
                         ,'${Control_ClassName}'  --process_id
                        )
                      """)
    }
  }
  
  def RegisterMASTER_USE(DefMaster: huemul_Table) {
    
    //Insert control_TablesUse
    if (huemulBigDataGov.RegisterInControl) {
      DefMaster._tablesUseId = LocalIdStep
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_TablesUse_add (
                                    '${DefMaster.TableName}'
                                   ,'${DefMaster.GetCurrentDataBase() }'  
                                   , '${Control_ClassName}' --as Process_Id
                                   , '${Control_Id}' --as ProcessExec_Id
                                   , '${DefMaster._tablesUseId}' --as ProcessExecStep_Id
                                   , ${this.getparamYear()}  -- TableUse_Year
                                   , ${this.getparamMonth()} -- TableUse_Month
                                   , ${this.getparamDay()} -- TableUse_Day  
                                   , ${this.getparamHour()} -- TableUse_Hour 
                                   , ${this.getparamMin()} -- TableUse_Miute
                                   , ${this.getparamSec()} -- TableUse_Second
                                   , '${AdditionalParamsInfo.replace("'", "''")}' -- TableUse_params
                                   , true --as TableUse_Read
                                   , false --as TableUse_Write
                                   , ${DefMaster.NumRows_New()} -- as TableUse_numRowsInsert
                                 , ${DefMaster.NumRows_Update()} -- as TableUse_numRowsUpdate
                                 , ${DefMaster.NumRows_Updatable() } -- as TableUse_numRowsUpdatable
                                 , ${DefMaster.NumRows_NoChange() } -- as TableUse_numRowsNoChange
                                 , ${DefMaster.NumRows_Delete()} -- as TableUse_numRowsMarkDelete
                                 , ${DefMaster.NumRows_Total()} -- as TableUse_numRowsTotal
                                 , ${if (DefMaster.getPartitionValue == null) "null" else s"'${DefMaster.getPartitionValue}'"} -- as PartitionValue
                                  ,'${Control_ClassName}'  --process_id
                                  )
                      """)     
    }
  }

  def RegisterMASTER_CREATE_Basic(DefMaster: huemul_Table) {
    if (huemulBigDataGov.IsTableRegistered(DefMaster.TableName))
      return
      
    if (huemulBigDataGov.DebugMode) println(s"HuemulControlLog: [${huemulBigDataGov.huemul_getDateForLog()}]    Register Table&Columns in Control")
    val LocalNewTable_id = huemulBigDataGov.huemul_GetUniqueId()

    if (huemulBigDataGov.RegisterInControl) {
      //Table
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s""" select control_Tables_addOrUpd(
                            '${LocalNewTable_id}'  --Table_id
                           ,null  --Area_Id
                           , '${DefMaster.GetCurrentDataBase()}' --as Table_BBDDName
                           , '${DefMaster.TableName}' --as Table_Name
                           , '${DefMaster.getDescription}' --as Table_Description
                           , '${DefMaster.getBusiness_ResponsibleName}' --as Table_BusinessOwner
                           , '${DefMaster.getIT_ResponsibleName}' --as Table_ITOwner
                           , '${DefMaster.getPartitionField}' --as Table_PartitionField
                           , '${DefMaster.getTableType}' --as Table_TableType
                           , '${DefMaster.getStorageType}' --as Table_StorageType
                           , '${DefMaster.getLocalPath}' --as Table_LocalPath
                           , '${DefMaster.GetPath(DefMaster.getGlobalPaths)}' --as Table_GlobalPath
                           , '' --as Table_SQLCreate
                           , '${DefMaster.getFrequency}' --as Table_Frequency
                           ,'${Control_ClassName}'  --process_id
                          )
      """)
    
      
      //Insert control_Columns
      var i: Integer = 0
      var localDatabaseName = DefMaster.GetCurrentDataBase()
      DefMaster.GetColumns().foreach { x => 
        val Column_Id = huemulBigDataGov.huemul_GetUniqueId()
    
        huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""SELECT control_Columns_addOrUpd (
        
                            '${Column_Id}' --Column_Id
                           , '${DefMaster.TableName}' --as Table_Name
                           , '${localDatabaseName}' --as Table_BBDDName
                           , ${i} --as Column_Position
                           , '${x.get_MyName()}' --as Column_Name
                           , '${x.Description}' --as Column_Description
                           , null --as Column_Formula
                           , '${x.DataType.sql}' --as Column_DataType
                           , false --as Column_SensibleData
                           , ${x.getMDM_EnableDTLog} --as Column_EnableDTLog
                           , ${x.getMDM_EnableOldValue} --as Column_EnableOldValue
                           , ${x.getMDM_EnableProcessLog} --as Column_EnableProcessLog
                           , ${if (x.getDefaultValue == null) "null" else s"'${x.getDefaultValue.replace("'", "''")}'"} --as Column_DefaultValue
                           , '${x.getSecurityLevel}' --as Column_SecurityLevel
                           , '${x.getEncryptedType}' --as Column_Encrypted
                           , '${x.getARCO_Data}' --as Column_ARCO
                           , ${x.getNullable} --as Column_Nullable
                           , ${x.getIsPK} --as Column_IsPK
                           , ${x.getIsUnique} --as Column_IsUnique
                           , ${x.getDQ_MinLen} --as Column_DQ_MinLen
                           , ${x.getDQ_MaxLen} --as Column_DQ_MaxLen
                           , ${x.getDQ_MinDecimalValue} --as Column_DQ_MinValue
                           , ${x.getDQ_MaxDecimalValue} --as Column_DQ_MaxValue
                           , ${if (x.getDQ_MinDateTimeValue == null) "null" else s"'${x.getDQ_MinDateTimeValue}'"} --as Column_DQ_MinDateTimeValue
                           , ${if (x.getDQ_MaxDateTimeValue == null) "null" else s"'${x.getDQ_MaxDateTimeValue}'"} --as Column_DQ_MaxDateTimeValue
                           , ${if (x.getDQ_RegExp == null) "null" else s"'${x.getDQ_RegExp}'" } --as Column_DQ_RegExp
                           ,'${Control_ClassName}'  --process_id
                      )""")        
          i += 1
        }
      
      //Insert control_tablesrel_add
      DefMaster.GetForeingKey().foreach { x =>       
        val p_tablerel_id = huemulBigDataGov.huemul_GetUniqueId()
        val InstanceTable = x._Class_TableName.asInstanceOf[huemul_Table]
        val localDatabaseName = InstanceTable.GetCurrentDataBase()
        val Resultado = huemulBigDataGov.postgres_connection.ExecuteJDBC_WithResult(s"""select control_tablesrel_add (
                                    '${p_tablerel_id}'    --p_tablerel_id
                                   ,'${InstanceTable.TableName }'  --p_table_Namepk
                                   ,'${localDatabaseName }'  --p_table_BBDDpk
  
                                   ,'${DefMaster.TableName }'  --p_table_NameFK
                                   ,'${DefMaster.GetCurrentDataBase() }'  --p_table_BBDDFK
  
                                   ,'${x.MyName }'  --p_TableFK_NameRelationship
  
                                   ,'${Control_ClassName}'  --process_id
                                  )
                      """)
                      
         val IdRel = Resultado.ResultSet(0).getString(0)
  
         x.Relationship.foreach { y => 
            huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_TablesRelCol_add (
                                      '${IdRel}'    --p_tablerel_id
                                     ,'${InstanceTable.TableName }'  --p_table_Namepk
                                     ,'${localDatabaseName }'  --p_table_BBDDpk
                                     ,'${y.PK.get_MyName() }'  --p_ColumnName_PK
  
                                     ,'${DefMaster.TableName }'  --p_table_NameFK
                                     ,'${DefMaster.GetCurrentDataBase() }'  --p_table_BBDDFK
                                     ,'${y.FK.get_MyName() }'  --p_ColumnName_FK    
  
                                     ,'${Control_ClassName}'  --process_id
                                    )
                        """)
          }
      }
    }
  }
  
  def RegisterMASTER_CREATE_Use(DefMaster: huemul_Table) {     
      
    if (huemulBigDataGov.RegisterInControl) {
      //Insert control_TablesUse
      DefMaster._tablesUseId = LocalIdStep
      huemulBigDataGov.postgres_connection.ExecuteJDBC_NoResulSet(s"""select control_TablesUse_add (
                                  '${DefMaster.TableName}'
                                 ,'${DefMaster.GetCurrentDataBase() }'  
                                 , '${Control_ClassName}' --as Process_Id
                                 , '${Control_Id}' --as ProcessExec_Id
                                 , '${DefMaster._tablesUseId}' --as ProcessExecStep_Id
                                   , ${this.getparamYear()}  -- TableUse_Year
                                   , ${this.getparamMonth()} -- TableUse_Month
                                   , ${this.getparamDay()} -- TableUse_Day  
                                   , ${this.getparamHour()} -- TableUse_Hour 
                                   , ${this.getparamMin()} -- TableUse_Miute
                                   , ${this.getparamSec()} -- TableUse_Second
                                   , null -- TableUse_params
                                 , false --as TableUse_Read
                                 , true --as TableUse_Write
                                 , ${DefMaster.NumRows_New()} -- as TableUse_numRowsInsert
                                 , ${DefMaster.NumRows_Update()} -- as TableUse_numRowsUpdate
                                 , ${DefMaster.NumRows_Updatable() } -- as TableUse_numRowsUpdatable
                                 , ${DefMaster.NumRows_NoChange() } -- as TableUse_numRowsNoChange
                                 , ${DefMaster.NumRows_Delete()} -- as TableUse_numRowsMarkDelete
                                 , ${DefMaster.NumRows_Total()} -- as TableUse_numRowsTotal
                                 , ${if (DefMaster.getPartitionValue == null) "null" else s"'${DefMaster.getPartitionValue}'"} -- as PartitionValue
                                 ,'${Control_ClassName}'  --process_id
                                )
                    """)
                    
      
    }
                        
  }
 
  
  def RaiseError(txt: String) {
    sys.error(txt)
  }
  
  
}