import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Table,  Panel, Form, FormGroup, 
        FormControl, InputGroup, ControlLabel, 
        HelpBlock, ButtonGroup, Button, Radio, Glyphicon } from 'react-bootstrap';
import User from '../model/User';
import {
  BrowserRouter as Router,
  Route,
  Link
} from 'react-router-dom'

class UserList extends Component {
    constructor(props) {
        super(props);
        this.state = {identities: [], isLoaded: false};
    }
    componentDidMount() {
        this.loadIdentities()
            .then((identities) => this.setState({identities: identities, isLoaded: true}));
    }
    render() {
        let loadingMessage = <h3>Loading, hold on!</h3>;
        let identitiesTable = this.state
                                    .identities
                                    .map((id, index) => 
                                            <tr key={index}>
                                                <td>{index+1}</td>
                                                <td><Link to={`/users/${id}`}>{id}</Link></td>
                                            </tr>
                                    );
        let table = (
            <Table>
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Identity</th>
                    </tr>
                </thead>
                <tbody>
                    {identitiesTable}
                </tbody>
            </Table>
        );
        return this.state.isLoaded ? table : loadingMessage;
        
    }
    loadIdentities() {
        return new Promise((success, fail) => {
            return fetch(`/users`)
                    .then(result => success(result.json()))
                    .catch(err => fail(err.json()));
        });
    }
}

export default UserList;