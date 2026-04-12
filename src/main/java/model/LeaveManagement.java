package model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
@DynamoDbBean
public class LeaveManagement{
    private String leaveId;
    private String fromDate;
    private String toDate;
    private String reason;
    private String status;

    private String pk;
    private String sk;
    private String type;

    private String name;
    private String passwordHash;
    private String approverEmail;
    private String role;
    private String email;

    public LeaveManagement(){}


    @DynamoDbPartitionKey
    public String getPk(){
        return pk;
    }
    public void setPk(String pk) {
        this.pk= pk;
    }
    @DynamoDbSortKey
    public String getSk(){
        return sk;
    }
    public void setSk(String sk) {
        this.sk= sk;
    }
    public String getType(){
        return type;
    }
    public void setType(String type) {
        this.type= type;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getApproverEmail() { return approverEmail; }
    public void setApproverEmail(String approverEmail) { this.approverEmail = approverEmail; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getLeaveId() {
        return leaveId;
    }
    public void setLeaveId(String leaveId) {
        this.leaveId = leaveId;
    }

    public String getToDate() {
        return toDate;
    }
    public void setToDate(String toDate) {
        this.toDate = toDate;
    }

    public String getFromDate() {
        return fromDate;
    }
    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getReason() {
        return reason;
    }
    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }


}