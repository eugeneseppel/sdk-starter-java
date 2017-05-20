import React, { Component } from 'react';
import PropTypes from 'prop-types';
import User from '../model/User';
import Binding from '../model/Binding';
import { Form, FormGroup, Button, ButtonGroup, Radio, OverlayTrigger, Tooltip, Checkbox,
        FormControl, InputGroup, ControlLabel, Glyphicon,
        HelpBlock } from 'react-bootstrap';
import _ from 'underscore';
import {
  BrowserRouter as Router,
  Route,
  Link
} from 'react-router-dom'
import MessengerPlugin from 'react-messenger-plugin/lib/MessengerPlugin';

class SendMessage extends Component {
    constructor(props){
        super(props);
    }
    render(){
        let html = `<div class="fb-send-to-messenger" 
                        messenger_app_id="1188086834670409" 
                        page_id="1227037150727016" 
                        data-ref="${this.props.identity}" 
                        color="blue" 
                        size="standard">
                    </div> `;
        let markup = {__html: html};
        return (
            <div dangerouslySetInnerHTML={markup}/>
        );
    }
    componentDidMount() {
        window.FB.init({
            appId: 1188086834670409,
            version: "v2.6",
            xfbml: true
        });
        window.FB.Event.subscribe('send_to_messenger', (e) => {
            if(e.event == "opt_in" && e.ref == this.props.identity){this.props.onMessengerRegistered()}; 
        });
    }
}

class UserDetails extends Component {
    constructor(props) {
        super(props);
        this.state = {
            isLoaded: false,
            isWorking: false,
            user: new User("none", [], "none"),
            isAddressIncorrect: false
        };
    }
    render() {
        let loadingIndicator = !this.state.isLoaded ? <h3>Loading, hold on!</h3> : null;
        let addressFields = this.state.user.bindings
                .map((binding, index) => {
                    let type = binding.type;
                    return (
                        <FormGroup  disabled={this.state.isWorking}
                                    controlId={`addressForm_${type}`} 
                                    key={`addressForm_${type}`}
                                    validationState={this.getValidationState(index)}>
                            <ControlLabel>{this.getPrettyTypeName(type)}</ControlLabel>
                            <InputGroup>
                                <InputGroup.Addon>
                                    <OverlayTrigger placement="left" overlay={<Tooltip id={`preferredTooltip_${index}`}>Set as preferred</Tooltip>}>
                                        <input 
                                            disabled={this.state.isWorking || binding.status.toLowerCase() == "not registered"}
                                            checked={this.state.user.preferred == type} 
                                            type="radio"
                                            onChange={this.handlePreferredChange.bind(this, index)} />
                                    </OverlayTrigger>
                                </InputGroup.Addon>
                                <FormControl 
                                            disabled={type != "sms" || this.state.isWorking}
                                            onChange={this.handleChange.bind(this, index)} 
                                            value={this.state.user.bindings[index].status} type="text"/>
                                <InputGroup.Addon>
                                    <OverlayTrigger placement="right" overlay={<Tooltip id={`offersTooltip_${index}`}>Receive offers</Tooltip>}>
                                        <input checked={this.state.user.bindings[index].offers} 
                                            disabled={this.state.isWorking}
                                            type="checkbox"
                                            onChange={this.handleOffersChange.bind(this, index)}/>
                                    </OverlayTrigger>
                                </InputGroup.Addon>
                            </InputGroup>
                            {type == "facebook-messenger" && binding.status.toLowerCase() == "not registered" ? 
                                <SendMessage onMessengerRegistered={this.onMessengerRegistered.bind(this, index)} identity={this.props.identity}/> : null}
                            <HelpBlock>{this.getValidationState(index) != "success" ? this.getHelpMessage(type) : ""}</HelpBlock>
                        </FormGroup>
                    );
                });
                let body = (
                    <Form onSubmit={this.handleSubmit.bind(this)}>
                        {addressFields}
                        <Button disabled={this.state.isWorking} type="submit" bsStyle="success"><Glyphicon glyph="ok"/> Save</Button>
                        <Button disabled={this.state.isWorking} type="button" bsStyle="link"><Link to="/">Cancel</Link></Button>
                    </Form>
                );
        return (
            <div>
                <h3>{this.props.identity}</h3>
                {this.state.isLoaded ? body : loadingIndicator}
            </div>
        );
    }
    componentDidMount() {
        this.loadUser()
            .then(user => this.setState({user: user, isLoaded: true}));
    }

    getPrettyTypeName(type) {
        switch(type) {
            case "sms": return "SMS";
            case "apn": return "iOS";
            case "facebook-messenger": return "Facebook Messenger";
            default: return "Unknown Address";
        }
    }
    onMessengerRegistered(index) {
        this.state.user.bindings[index].status = "Registered";
        this.setState({user: this.state.user});
    }
    doSave(user) {
        console.dir(user);
        this.setState({isWorking: true});
        this.saveBindings(user.bindings)
            .then(() => this.saveUser(user))
            .then(() => this.props.history.push("/"))
            .catch(error => {
               console.log(error);
               this.setState({isWorking: false, isAddressIncorrect: true}); 
            });
            
    }
    saveUser(user) {
        let identity = this.props.identity;
        return new Promise((success, fail) => {
            return fetch(`/api/users/${identity}/config`, {
                        method: "POST", 
                        body: JSON.stringify({preferred: user.preferred}),
                        headers: {
                            'Accept': 'application/json, text/plain, */*',
                            'Content-Type': 'application/json'
                        }
                    })
                    .then(res => {
                        if(res.ok) {
                            success(res.json());
                        } else {
                            throw Error(res.statusText);
                        }
                    })
                    .catch(err => fail(err));
        });
    }
    saveBindings(bindings) {
        let identity = this.props.identity;
        let promises = bindings
                .filter((binding, index) => 
                            this.getValidationState(index) == "success" 
                            && binding.status.toLowerCase() != "not registered"
                )
                .map(binding => {
                    return new Promise((resolve, reject) => {
                        return fetch(`/api/users/${identity}/bindings`, {
                                        method: "POST", 
                                        body: JSON.stringify({type: binding.type, address: binding.status, acceptOffers: binding.offers}),
                                        headers: {
                                            'Accept': 'application/json, text/plain, */*',
                                            'Content-Type': 'application/json'
                                        }
                                    })
                                    .then(response => {
                                        if(response.ok) {
                                            resolve(response.json());
                                        } else {
                                            throw Error(response.statusText);
                                        }
                                    }).catch(err => reject(err));
                    });
                });
        return Promise.all(promises);
    }
    handleSubmit(event) {
        event.preventDefault();
        console.dir(this.state.user);
        let user = this.state.user;
        let valid = user.bindings.map((value, index) => this.getValidationState(index) == "success").reduce((prev, cur) => prev && cur);
        if(valid){
            this.doSave(user);
        }
    }
    handleChange(index, event) {
        let user = this.state.user;
        let binding = user.bindings[index];
        switch(binding.type){
            case "sms": binding.status = event.target.value; break;
            case "apn": break;
            case "facebook-messenger": break;
        }
        this.setState({user: user, isAddressIncorrect: false});
    }
    handlePreferredChange(index, event){
        let user = this.state.user;
        let binding = user.bindings[index];
        user.preferred = binding.type;
        this.setState({user: user});
    }
    handleOffersChange(index, event){
        let user = this.state.user;
        let binding = user.bindings[index];
        binding.offers = event.target.checked;
        this.setState({user: user});
    }
    getValidationState(index) {
        let binding = this.state.user.bindings[index];
        switch(binding.type) {
            case "sms": return /^\+?[1-9]\d{1,14}$/.exec(binding.status) && !this.state.isAddressIncorrect ? "success" : "error";
            case "apn": return "success";
            case "facebook-messenger": return "success";
        }
    }
    getHelpMessage(bindingType) {
        switch(bindingType) {
            case "sms": return "Should be an E.164 phone number!";
            case "apn": return "Incorrect address!";
            case "facebook-messenger": return "Incorrect address!";
        }
    }
    loadUser() {
        let identity = this.props.identity;
        return new Promise((success, fail) => {
            return fetch(`/api/users/${identity}`)
                    .then(result => success(result.json()));
        });
    }
}
UserDetails.propTypes = {
    identity: PropTypes.string.isRequired
}

export default UserDetails;