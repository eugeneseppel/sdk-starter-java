import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Table,  Panel, Form, FormGroup, Grid, Row, Col, 
        FormControl, InputGroup, ControlLabel, Image,
        HelpBlock, ButtonGroup, Button, Radio, Glyphicon } from 'react-bootstrap';
import User from '../model/User';
import {
  BrowserRouter as Router,
  Route,
  Link
} from 'react-router-dom';
import logo from '../notify_beta_header.png';

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
                                                <td><Link to={`/users/${id}`}>{id}</Link></td>
                                            </tr>
                                    );
        let table = (
            <div>
                <Image responsive src={logo} rounded />
                <Grid fluid>
                    <Row>
                        <Col xs={12} md={4} mdPush={3}>
                            <Table>
                                <thead>
                                    <tr>
                                        <th>Identity</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {identitiesTable}
                                </tbody>
                            </Table>
                        </Col>
                    </Row>
                </Grid>
            </div>
        );
        return this.state.isLoaded ? table : loadingMessage;
        
    }
    loadIdentities() {
        return new Promise((success, fail) => {
            return fetch(`/api/users`)
                    .then(result => success(result.json()))
                    .catch(err => fail(err.json()));
        });
    }
}

export default UserList;