import React, { Component } from 'react';
import { Grid, Row, Col } from 'react-bootstrap';
import UserList from './components/UserList';
import UserDetails from './components/UserDetails';
import {
  BrowserRouter as Router,
  Route,
  Link
} from 'react-router-dom'

const UserListComponent = () => <UserList/>;
const UserDetailsComponent = ({match, history}) => <UserDetails history={history} identity={match.params.identity}/>;

class App extends Component {
  render() {
    return (
      <Router>
        <Grid>
          <Row>
            <Col xs={12} md={8} mdPush={2}>
              <Route exact path="/" component={UserList}></Route>
              <Route path={"/users/:identity"} component={UserDetailsComponent}/>
            </Col>
          </Row>
        </Grid>
      </Router>
    );
  }
}

export default App;
